package com.deniscerri.ytdl

import com.deniscerri.ytdl.spotify.SpotifyResourceType
import com.deniscerri.ytdl.spotify.SpotifyTrackMetadata
import com.deniscerri.ytdl.spotify.SpotifyUrlParser
import com.deniscerri.ytdl.spotify.distinctTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyUrlParserTest {
    @Test
    fun parsesTrackAndDropsTrackingParameters() {
        val resource = SpotifyUrlParser.parse(
            "https://open.spotify.com/track/11dFghVXANMlKmJXsNCbNl?si=tracking"
        )

        requireNotNull(resource)
        assertEquals(SpotifyResourceType.TRACK, resource.type)
        assertEquals("11dFghVXANMlKmJXsNCbNl", resource.id)
        assertEquals("https://open.spotify.com/track/11dFghVXANMlKmJXsNCbNl", resource.originalUrl)
    }

    @Test
    fun recognizesAlbumsPlaylistsAndArtists() {
        assertEquals(SpotifyResourceType.ALBUM, SpotifyUrlParser.parse("https://open.spotify.com/album/1A2B3C4D5E6F7G8H9I0J")?.type)
        assertEquals(SpotifyResourceType.PLAYLIST, SpotifyUrlParser.parse("https://open.spotify.com/playlist/1A2B3C4D5E6F7G8H9I0J")?.type)
        assertEquals(SpotifyResourceType.ARTIST, SpotifyUrlParser.parse("https://open.spotify.com/artist/1A2B3C4D5E6F7G8H9I0J")?.type)
    }

    @Test
    fun recognizesPlaylistLinksForBulkDownload() {
        val resource = SpotifyUrlParser.parse(
            "https://open.spotify.com/playlist/3zV94027chLdU92Hrp4LO6?si=tracking"
        )

        assertEquals(SpotifyResourceType.PLAYLIST, resource?.type)
    }

    @Test
    fun rejectsNonSpotifyAndMalformedUrls() {
        assertNull(SpotifyUrlParser.parse("https://youtube.com/watch?v=abc"))
        assertNull(SpotifyUrlParser.parse("https://open.spotify.com/track/short"))
        assertNull(SpotifyUrlParser.parse("not a url"))
    }

    @Test
    fun removesRepeatedSpotifyTrackIdsButPreservesDifferentTracks() {
        fun track(id: String, title: String) = SpotifyTrackMetadata(
            title = title,
            artist = "Artist",
            artworkUrl = null,
            spotifyUrl = "https://open.spotify.com/track/$id"
        )

        val tracks = listOf(
            track("sameTrackId", "First"),
            track("sameTrackId", "First copy"),
            track("otherTrack", "Other")
        )

        assertEquals(listOf("First", "Other"), tracks.distinctTracks().map { it.title })
    }
}
