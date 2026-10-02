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
    val spotifyUrl: String
) {
    val searchQuery: String
        get() = "$artist - $title"
}
