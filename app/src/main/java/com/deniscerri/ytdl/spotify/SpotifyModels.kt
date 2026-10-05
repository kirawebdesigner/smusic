package com.deniscerri.ytdl.spotify

/** The public Spotify resource types understood by smusic. */
enum class SpotifyResourceType {
    TRACK,
    ALBUM,
    PLAYLIST,
    ARTIST,
    UNKNOWN
}

data class SpotifyResource(
    val type: SpotifyResourceType,
    val id: String,
    val originalUrl: String
)

data class SpotifyTrackMetadata(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val spotifyUrl: String,
    val durationMs: Long? = null,
    val playlistIndex: Int? = null
) {
    val searchQuery: String
        get() = "$artist - $title"

    /** Prefer the Spotify track ID; fall back to normalized metadata if a payload omits it. */
    val uniqueKey: String
        get() = spotifyUrl.substringAfterLast("/track/", "")
            .substringBefore('?')
            .takeIf { it.isNotBlank() && it != spotifyUrl }
            ?.let { "spotify:$it" }
            ?: "metadata:${artist.trim().lowercase()}\u0000${title.trim().lowercase()}"
}

fun List<SpotifyTrackMetadata>.distinctTracks(): List<SpotifyTrackMetadata> =
    distinctBy { it.uniqueKey }
