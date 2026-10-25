package com.vista.mirror.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vista.mirror.MainActivity
import com.vista.mirror.R
import com.vista.mirror.stream.DlnaPushController
import com.vista.mirror.stream.HttpLiveStreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.vista.mirror.START"
        const val ACTION_STOP = "com.vista.mirror.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_TV_CONTROL_URL = "extra_tv_control_url"
        const val EXTRA_TV_NAME = "extra_tv_name"
        const val NOTIFICATION_ID = 4040
        const val CHANNEL_ID = "vista_mirror_channel"

        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var audioCaptureHelper: AudioCaptureHelper? = null
    private var streamServer: HttpLiveStreamServer? = null
    private val dlnaController = DlnaPushController()
    private var tvControlUrl: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                tvControlUrl = intent.getStringExtra(EXTRA_TV_CONTROL_URL) ?: ""
                val tvName = intent.getStringExtra(EXTRA_TV_NAME) ?: "Телевизор"

                if (resultCode != 0 && resultData != null) {
                    startForeground(NOTIFICATION_ID, createNotification(tvName))
                    startMirroring(resultCode, resultData)
                }
            }
            ACTION_STOP -> {
                stopMirroring()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMirroring(resultCode: Int, resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

        val metrics = resources.displayMetrics
        val width = 1280
        val height = 720
        val dpi = metrics.densityDpi

        // Setup local HTTP live streaming server on port 8080
        streamServer = HttpLiveStreamServer(8080).apply {
            try {
                start()
            } catch (_: Exception) {}
        }

        // Setup ImageReader for capturing screen frames
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader?.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val planes = image.planes
                    val buffer: ByteBuffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val cropped = if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    val out = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 75, out)
                    val jpegBytes = out.toByteArray()

                    streamServer?.pushJpegFrame(jpegBytes)
                    if (cropped != bitmap) cropped.recycle()
                    bitmap.recycle()
                } catch (_: Exception) {
                } finally {
                    image.close()
                }
            }, null)
        }

        // Create VirtualDisplay
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "VistaScreenMirror",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        // Capture Internal System Audio
        mediaProjection?.let { projection ->
            audioCaptureHelper = AudioCaptureHelper(applicationContext).apply {
                startAudioCapture(projection, serviceScope) { _ ->
                    // Audio stream packet handling
                }
            }
        }

        // If target TV is Sony Bravia 2013 / DLNA DMR, push the live stream directly to the TV
        if (tvControlUrl.isNotEmpty()) {
            serviceScope.launch {
                val localIp = getDeviceIpAddress()
                val liveStreamUrl = "http://$localIp:8080/live.mjpg"
                dlnaController.playStreamOnTv(tvControlUrl, liveStreamUrl)
            }
        }

        isRunning = true
    }

    private fun stopMirroring() {
        isRunning = false
        if (tvControlUrl.isNotEmpty()) {
            serviceScope.launch {
                dlnaController.stopStreamOnTv(tvControlUrl)
            }
        }
        audioCaptureHelper?.stop()
        audioCaptureHelper = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        streamServer?.stop()
        streamServer = null
        serviceScope.cancel()
    }

    private fun getDeviceIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            return InetAddress.getByAddress(
                byteArrayOf(
                    (ipInt and 0xff).toByte(),
                    (ipInt shr 8 and 0xff).toByte(),
                    (ipInt shr 16 and 0xff).toByte(),
                    (ipInt shr 24 and 0xff).toByte()
                )
            ).hostAddress ?: "127.0.0.1"
        } catch (_: Exception) {
            return "127.0.0.1"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Трансляция экрана",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(tvName: String): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Виста дублирует экран")
            .setContentText("Трансляция экрана и звука на $tvName")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopMirroring()
        super.onDestroy()
    }
}
