package com.vista.mirror.stream

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * WebRTC engine for low-latency direct P2P stream over Wi-Fi.
 */
class WebRtcBroadcaster(private val context: Context) {

    private val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun createMediaTracks(): Pair<VideoTrack?, AudioTrack?> {
        val factory = peerConnectionFactory ?: return Pair(null, null)

        surfaceTextureHelper = SurfaceTextureHelper.create("VistaCaptureThread", eglBase.eglBaseContext)
        videoSource = factory.createVideoSource(false)
        videoTrack = factory.createVideoTrack("VISTA_VIDEO_TRACK", videoSource)

        val audioConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(audioConstraints)
        audioTrack = factory.createAudioTrack("VISTA_AUDIO_TRACK", audioSource)

        return Pair(videoTrack, audioTrack)
    }

    fun getEglBaseContext(): EglBase.Context = eglBase.eglBaseContext

    fun release() {
        videoTrack?.dispose()
        audioTrack?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        surfaceTextureHelper?.dispose()
        peerConnectionFactory?.dispose()
        eglBase.release()
    }
}
