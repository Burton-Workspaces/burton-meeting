package com.burton.meeting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.burton.meeting.report.ShakeToReport
import com.burton.meeting.ui.call.CallScreen
import com.burton.meeting.ui.home.HomeScreen
import com.burton.meeting.ui.home.HomeViewModel
import com.burton.meeting.ui.navigation.Routes
import com.burton.meeting.ui.theme.BurtonBlack
import com.burton.meeting.ui.theme.BurtonIvory
import com.burton.meeting.ui.theme.BurtonMeetingTheme
import com.burton.meeting.ui.theme.BurtonMute
import com.burton.meeting.ui.theme.BurtonVoid
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var permitted by mutableStateOf(false)
    private var pendingMedia: (() -> Unit)? = null
    private val shakeToReport by lazy { ShakeToReport(this) }

    private val discoveryLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permitted = granted || Build.VERSION.SDK_INT < 33
    }

    private val mediaLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val ok = result.filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }
            .all { it.value }
        if (ok) pendingMedia?.invoke()
        pendingMedia = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permitted = hasDiscoveryPermission()
        if (!permitted) requestDiscoveryPermission()
        setContent {
            BurtonMeetingTheme {
                if (permitted) {
                    BurtonApp(onNeedMedia = ::requestMediaPermissions)
                } else {
                    PermissionGate(onRequest = ::requestDiscoveryPermission)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shakeToReport.start()
    }

    override fun onPause() {
        shakeToReport.stop()
        super.onPause()
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun requestMediaPermissions(video: Boolean, onGranted: () -> Unit) {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED &&
                it != Manifest.permission.BLUETOOTH_CONNECT &&
                it != Manifest.permission.POST_NOTIFICATIONS
        }
        val optionalMissing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            if (optionalMissing.isNotEmpty()) mediaLauncher.launch(optionalMissing.toTypedArray())
            onGranted()
            return
        }
        pendingMedia = onGranted
        mediaLauncher.launch(needed.toTypedArray())
    }

    private fun hasDiscoveryPermission(): Boolean {
        val permission = requiredDiscoveryPermission()
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < 33
    }

    private fun requiredDiscoveryPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

    private fun requestDiscoveryPermission() {
        discoveryLauncher.launch(requiredDiscoveryPermission())
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BurtonBlack)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Burton Meeting", style = MaterialTheme.typography.headlineLarge, color = BurtonIvory)
        Spacer(Modifier.height(12.dp))
        Text(
            "Local meeting discovery uses Wi-Fi. Allow nearby devices so the app can find rooms on this network.",
            style = MaterialTheme.typography.bodyLarge,
            color = BurtonMute,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = BurtonIvory, contentColor = BurtonVoid),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Allow discovery")
        }
    }
}

@Composable
private fun BurtonApp(onNeedMedia: (Boolean, () -> Unit) -> Unit) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val session by homeViewModel.session.collectAsStateWithLifecycle()
    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity
    LaunchedEffect(session) {
        val route = navController.currentDestination?.route
        if (session != null && route != Routes.CALL) {
            navController.navigate(Routes.CALL)
        } else if (session == null && route == Routes.CALL) {
            navController.popBackStack(Routes.HOME, false)
        }
    }
    DisposableEffect(session != null, activity) {
        activity?.keepScreenOn(session != null)
        onDispose { activity?.keepScreenOn(false) }
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BurtonBlack),
        containerColor = BurtonBlack,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .statusBarsPadding(),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNeedPermissions = onNeedMedia,
                    viewModel = homeViewModel,
                )
            }
            composable(Routes.CALL) {
                CallScreen()
            }
        }
    }
}
