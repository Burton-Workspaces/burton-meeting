package com.burton.meeting.ui.components

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoPane(
    track: VideoTrack?,
    eglContext: EglBase.Context?,
    mirror: Boolean,
    modifier: Modifier = Modifier,
) {
    val bound = remember { arrayOfNulls<VideoTrack>(1) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                if (eglContext != null) {
                    init(eglContext, null)
                    setEnableHardwareScaler(true)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                }
                setMirror(mirror)
            }
        },
        update = { view ->
            view.setMirror(mirror)
            val previous = bound[0]
            if (previous !== track) {
                previous?.removeSink(view)
                track?.addSink(view)
                bound[0] = track
            }
        },
        onRelease = { view ->
            bound[0]?.removeSink(view)
            bound[0] = null
            view.release()
        },
    )
    DisposableEffect(track) {
        onDispose { }
    }
}
