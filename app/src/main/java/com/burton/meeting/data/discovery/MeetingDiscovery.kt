package com.burton.meeting.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.NearbyMeeting
import com.burton.meeting.domain.RoomCodes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val found = ConcurrentHashMap<String, NearbyMeeting>()
    private val _nearby = MutableStateFlow<List<NearbyMeeting>>(emptyList())
    val nearby: StateFlow<List<NearbyMeeting>> = _nearby

    private var browsing = false
    private var multicast: WifiManager.MulticastLock? = null
    private var advertised: NsdServiceInfo? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        override fun onDiscoveryStarted(serviceType: String?) = Unit
        override fun onDiscoveryStopped(serviceType: String?) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            val name = serviceInfo?.serviceName ?: return
            found.keys.removeAll { it.startsWith("$name@") }
            publish()
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            if (serviceInfo == null) return
            if (serviceInfo.serviceType?.contains("burton-meeting") != true &&
                serviceInfo.serviceName?.startsWith("burton-meeting-") != true
            ) {
                return
            }
            nsd.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo?) {
                        val meeting = resolved?.toMeeting() ?: return
                        found["${resolved.serviceName}@${meeting.host}"] = meeting
                        publish()
                    }
                },
            )
        }
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            advertised = serviceInfo
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            advertised = null
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
    }

    fun startBrowsing() {
        if (browsing) return
        browsing = true
        multicast = wifi.createMulticastLock("burton-meeting").apply {
            setReferenceCounted(false)
            acquire()
        }
        nsd.discoverServices(RoomCodes.NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopBrowsing() {
        if (!browsing) return
        browsing = false
        runCatching { nsd.stopServiceDiscovery(discoveryListener) }
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
        found.clear()
        publish()
    }

    fun advertise(name: String, code: String, mode: CallMode, port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = "burton-meeting-$code"
            serviceType = RoomCodes.NSD_TYPE
            this.port = port
            setAttribute("name", name.take(48))
            setAttribute("code", code)
            setAttribute("mode", mode.name.lowercase())
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun stopAdvertising() {
        if (advertised == null) return
        runCatching { nsd.unregisterService(registrationListener) }
        advertised = null
    }

    fun findByCode(code: String): NearbyMeeting? {
        val normalized = RoomCodes.normalize(code)
        return found.values.firstOrNull { it.code == normalized }
    }

    private fun NsdServiceInfo.toMeeting(): NearbyMeeting? {
        val host = host?.hostAddress ?: return null
        val attrs = attributes.mapKeys { it.key } 
        fun attr(key: String): String =
            attrs[key]?.toString(Charsets.UTF_8)
                ?: attrs.entries.firstOrNull { it.key.equals(key, true) }?.value?.toString(Charsets.UTF_8)
                ?: ""
        val code = RoomCodes.normalize(attr("code").ifBlank { serviceName.removePrefix("burton-meeting-") })
        if (!RoomCodes.isValid(code)) return null
        return NearbyMeeting(
            code = code,
            name = attr("name").ifBlank { "Meeting" },
            mode = CallMode.parse(attr("mode")),
            host = host,
            port = if (port > 0) port else RoomCodes.SIGNAL_PORT,
        )
    }

    private fun publish() {
        _nearby.value = found.values.distinctBy { it.code }.sortedBy { it.name.lowercase() }
    }
}
