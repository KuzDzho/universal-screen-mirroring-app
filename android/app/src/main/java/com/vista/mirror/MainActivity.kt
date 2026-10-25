package com.vista.mirror

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vista.mirror.discovery.DiscoveredTv
import com.vista.mirror.discovery.TvDiscoveryManager
import com.vista.mirror.service.ScreenCaptureService
import com.vista.mirror.ui.MainScreen
import com.vista.mirror.ui.theme.VistaMirrorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var discoveryManager: TvDiscoveryManager
    private var pendingTv: DiscoveredTv? = null
    private var activeTvId by mutableStateOf<String?>(null)
    private var isMirroring by mutableStateOf(false)

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val targetTv = pendingTv
            if (targetTv != null) {
                startCaptureService(result.resultCode, result.data!!, targetTv)
                activeTvId = targetTv.id
                isMirroring = true
            }
        } else {
            Toast.makeText(this, "Доступ к экрану не предоставлен", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Continue discovery
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discoveryManager = TvDiscoveryManager(this)

        requestRequiredPermissions()

        setContent {
            VistaMirrorTheme {
                val tvList by discoveryManager.discoveredTvs.collectAsState()

                MainScreen(
                    discoveredTvs = tvList,
                    activeTvId = activeTvId,
                    isMirroring = isMirroring,
                    onTvSelected = { tv ->
                        requestScreenMirror(tv)
                    },
                    onStopMirroring = {
                        stopCaptureService()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isMirroring = ScreenCaptureService.isRunning
        lifecycleScope.launch {
            discoveryManager.startDiscovery()
        }
    }

    override fun onPause() {
        super.onPause()
        discoveryManager.stopDiscovery()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun requestScreenMirror(tv: DiscoveredTv) {
        pendingTv = tv
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mpManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startCaptureService(resultCode: Int, resultData: Intent, tv: DiscoveredTv) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenCaptureService.EXTRA_TV_CONTROL_URL, tv.controlUrl)
            putExtra(ScreenCaptureService.EXTRA_TV_NAME, tv.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopCaptureService() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(serviceIntent)
        activeTvId = null
        isMirroring = false
    }
}
