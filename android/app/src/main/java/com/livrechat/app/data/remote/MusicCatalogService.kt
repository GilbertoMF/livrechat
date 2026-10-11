package com.livrechat.app.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val previewUrl: String,
    val durationFormatted: String = "0:30"
)

object MusicCatalogService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun searchMusic(query: String, limit: Int = 30): List<MusicTrack> = withContext(Dispatchers.IO) {
        val safeQuery = query.trim()
        if (safeQuery.isEmpty()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(safeQuery, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=$limit"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = gson.fromJson(body, JsonObject::class.java)
                val results = json.getAsJsonArray("results") ?: return@withContext emptyList()

                val list = mutableListOf<MusicTrack>()
                for (element in results) {
                    val obj = element.asJsonObject
                    val trackId = obj.get("trackId")?.asString ?: obj.get("collectionId")?.asString ?: continue
                    val trackName = obj.get("trackName")?.asString ?: continue
                    val artistName = obj.get("artistName")?.asString ?: "Artista"
                    val previewUrl = obj.get("previewUrl")?.asString ?: continue
                    val artworkUrl = obj.get("artworkUrl100")?.asString
                        ?: obj.get("artworkUrl60")?.asString ?: ""

                    // Upgrade artwork resolution to 600x600 if it's itunes artwork
                    val highResArt = if (artworkUrl.contains("100x100bb")) {
                        artworkUrl.replace("100x100bb", "600x600bb")
                    } else artworkUrl

                    val durationMs = obj.get("trackTimeMillis")?.asLong ?: 30000L
                    val mins = (durationMs / 1000) / 60
                    val secs = (durationMs / 1000) % 60
                    val durationFormatted = String.format("%d:%02d", mins, secs)

                    list.add(
                        MusicTrack(
                            id = trackId,
                            title = trackName,
                            artist = artistName,
                            albumArtUrl = highResArt,
                            previewUrl = previewUrl,
                            durationFormatted = durationFormatted
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Gerenciamento de Músicas Favoritas no SharedPreferences
    private const val PREFS_FAVORITES = "music_favorites_prefs"
    private const val KEY_FAVORITES_JSON = "saved_favorites_json"

    fun getFavoriteTracks(context: Context): List<MusicTrack> {
        val sp = context.getSharedPreferences(PREFS_FAVORITES, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_FAVORITES_JSON, null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<MusicTrack>>() {}.type
            gson.fromJson<List<MusicTrack>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isFavorite(context: Context, trackId: String): Boolean {
        val favs = getFavoriteTracks(context)
        return favs.any { it.id == trackId }
    }

    fun toggleFavorite(context: Context, track: MusicTrack): Boolean {
        val sp = context.getSharedPreferences(PREFS_FAVORITES, Context.MODE_PRIVATE)
        val current = getFavoriteTracks(context).toMutableList()
        val index = current.indexOfFirst { it.id == track.id }
        val nowFavorite: Boolean
        if (index >= 0) {
            current.removeAt(index)
            nowFavorite = false
        } else {
            current.add(0, track)
            nowFavorite = true
        }
        sp.edit().putString(KEY_FAVORITES_JSON, gson.toJson(current)).apply()
        return nowFavorite
    }
}
