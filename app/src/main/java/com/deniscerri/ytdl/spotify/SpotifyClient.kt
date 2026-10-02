package com.deniscerri.ytdl.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** Reads Spotify's public embed payload; no Spotify account or client secret is required. */
class SpotifyClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolve(resource: SpotifyResource): List<SpotifyTrackMetadata> = withContext(Dispatchers.IO) {
        when (resource.type) {
            SpotifyResourceType.TRACK -> listOf(resolveTrack(resource))
            SpotifyResourceType.PLAYLIST, SpotifyResourceType.ALBUM -> resolveCollection(resource)
            SpotifyResourceType.ARTIST, SpotifyResourceType.UNKNOWN -> throw IOException(
                "Spotify artist links are not supported yet. Paste a track, album, or playlist link."
            )
        }
    }

    suspend fun resolvePublicTrack(resource: SpotifyResource): SpotifyTrackMetadata = resolve(resource).first()

    private fun resolveTrack(resource: SpotifyResource): SpotifyTrackMetadata {
        val entity = fetchEntity(resource)
        return trackFromEntity(entity, resource.originalUrl, null)
    }

    private fun resolveCollection(resource: SpotifyResource): List<SpotifyTrackMetadata> {
        val entity = fetchEntity(resource)
        val artwork = entity["coverArt"]?.jsonObject?.get("sources")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        return entity["trackList"]?.jsonArray.orEmpty().mapIndexedNotNull { index, element ->
            val track = element.jsonObject
            if (track["isPlayable"]?.jsonPrimitive?.booleanOrNull == false) return@mapIndexedNotNull null
            val title = track["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val artist = track["subtitle"]?.jsonPrimitive?.contentOrNull
                ?.replace('\u00a0', ' ')
                ?.trim()
                .orEmpty()
            if (title.isBlank() || artist.isBlank()) return@mapIndexedNotNull null
            SpotifyTrackMetadata(
                title = title,
                artist = artist,
                artworkUrl = artwork,
                spotifyUrl = track["uri"]?.jsonPrimitive?.contentOrNull
                    ?.replace("spotify:track:", "https://open.spotify.com/track/")
                    ?: resource.originalUrl,
                durationMs = track["duration"]?.jsonPrimitive?.longOrNull,
                playlistIndex = index + 1
            )
        }
    }

    private fun trackFromEntity(entity: JsonObject, originalUrl: String, index: Int?): SpotifyTrackMetadata {
        val artists = entity["artists"]?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString(", ")
            .orEmpty()
        val artist = artists.ifBlank {
            entity["subtitle"]?.jsonPrimitive?.contentOrNull?.replace('\u00a0', ' ')?.trim()
                .orEmpty()
        }
        val artwork = entity["visualIdentity"]?.jsonObject?.get("image")?.jsonArray
            ?.maxByOrNull { it.jsonObject["maxWidth"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        val id = entity["id"]?.jsonPrimitive?.contentOrNull
        return SpotifyTrackMetadata(
            title = entity["title"]?.jsonPrimitive?.contentOrNull
                ?: entity["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artist = artist.ifBlank { "Unknown artist" },
            artworkUrl = artwork,
            spotifyUrl = id?.let { "https://open.spotify.com/track/$it" } ?: originalUrl,
            durationMs = entity["duration"]?.jsonPrimitive?.longOrNull,
            playlistIndex = index
        )
    }

    private fun fetchEntity(resource: SpotifyResource): JsonObject {
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/${resource.type.name.lowercase()}/${resource.id}")
            .header("Accept", "text/html")
            .header("User-Agent", "smusic/1.0")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Spotify metadata request failed (${response.code})")
            val html = response.body?.string().orEmpty()
            val script = Regex("<script id=\\\"__NEXT_DATA__\\\" type=\\\"application/json\\\">(.*?)</script>")
                .find(html)?.groupValues?.getOrNull(1)
                ?: throw IOException("Spotify did not return public metadata")
            return json.parseToJsonElement(script).jsonObject
                .getValue("props").jsonObject.getValue("pageProps").jsonObject
                .getValue("state").jsonObject.getValue("data").jsonObject
                .getValue("entity").jsonObject
        }
    }
}

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = if (isString || content != "null") content else null

private val kotlinx.serialization.json.JsonPrimitive.longOrNull: Long?
    get() = content.toLongOrNull()

private val kotlinx.serialization.json.JsonPrimitive.intOrNull: Int?
    get() = content.toIntOrNull()

private val kotlinx.serialization.json.JsonPrimitive.booleanOrNull: Boolean?
    get() = content.toBooleanStrictOrNull()
