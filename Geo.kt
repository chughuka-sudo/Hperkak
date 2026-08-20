package com.dragon.tiktok.utils

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface

data class GeoData(val ip: String, val country: String, val city: String)

object IPGeoHelper {
    private val client = OkHttpClient()
    private val gson = Gson()

    fun getGeo(): GeoData {
        return try {
            // Сначала внешний IP
            val ipRequest = Request.Builder().url("https://api.ipify.org").build()
            val ipResponse = client.newCall(ipRequest).execute()
            val ip = ipResponse.body?.string()?.trim() ?: "unknown"

            // Гео по IP
            val geoRequest = Request.Builder().url("http://ip-api.com/json/$ip").build()
            val geoResponse = client.newCall(geoRequest).execute()
            val json = geoResponse.body?.string() ?: "{}"
            val map = gson.fromJson(json, Map::class.java) as Map<*, *>
            val country = map["country"] as? String ?: "unknown"
            val city = map["city"] as? String ?: "unknown"
            GeoData(ip, country, city)
        } catch (e: Exception) {
            GeoData("unknown", "unknown", "unknown")
        }
    }
}