package com.deniscerri.ytdl.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object SpotifyUrlParser {
    private val supportedHosts = setOf("open.spotify.com", "play.spotify.com")

    fun parse(input: String): SpotifyResource? {
        val url = input.trim().toHttpUrlOrNull() ?: return null
        if (url.host !in supportedHosts) return null
        val segments = url.pathSegments.filter(String::isNotBlank)
        if (segments.size < 2) return null
        val type = when (segments[0].lowercase()) {
            "track" -> SpotifyResourceType.TRACK
            "album" -> SpotifyResourceType.ALBUM
            "playlist" -> SpotifyResourceType.PLAYLIST
            "artist" -> SpotifyResourceType.ARTIST
            else -> SpotifyResourceType.UNKNOWN
        }
        if (type == SpotifyResourceType.UNKNOWN) return null
        val id = segments[1].takeIf { it.matches(Regex("[A-Za-z0-9]{10,}")) } ?: return null
        return SpotifyResource(type, id, "https://open.spotify.com/${segments[0]}/$id")
    }

    fun isSpotifyUrl(input: String): Boolean = parse(input) != null
}

class SpotifyClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolvePublicTrack(resource: SpotifyResource): SpotifyTrackMetadata = withContext(Dispatchers.IO) {
        require(resource.type == SpotifyResourceType.TRACK) { "Only Spotify track links can be resolved currently" }
        val request = Request.Builder()
            .url("https://open.spotify.com/oembed?url=${resource.originalUrl}")
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Spotify metadata request failed (${response.code})")
            val body = response.body?.string().orEmpty()
            val metadata = json.decodeFromString<SpotifyOEmbed>(body)
            val artist = metadata.authorName?.trim().orEmpty().ifBlank { "Unknown artist" }
            val title = metadata.title.trim()
            if (title.isBlank()) throw IOException("Spotify returned an empty track title")
            SpotifyTrackMetadata(title, artist, metadata.thumbnailUrl, resource.originalUrl)
        }
    }

    @Serializable
    private data class SpotifyOEmbed(
        val title: String = "",
        @SerialName("author_name") val authorName: String? = null,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null
    )
}
