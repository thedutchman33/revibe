package com.revibe.provider

import com.revibe.image.artworkColorSourceUrl
import com.revibe.image.highResolutionArtworkUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkMatchingTest {

    @Test
    fun `titles ignore release and video qualifiers`() {
        assertTrue(artworkTitlesMatch("Gehra Hua", "Gehra Hua (From \"Dhurandhar\")"))
        assertTrue(artworkTitlesMatch("Kesariya", "Kesariya | Official Video"))
        assertFalse(artworkTitlesMatch("Gehra Hua", "Gehra Hua (Eclipsa Audio)"))
        assertFalse(artworkTitlesMatch("One More Time", "One More Time (Remix)"))
    }

    @Test
    fun `artist matching requires every requested artist`() {
        assertTrue(artworkArtistsMatch("Arijit Singh", "Shashwat Sachdev, Arijit Singh & Armaan Khan"))
        assertTrue(artworkArtistsMatch("Anuv Jain", "Anuv Jain"))
        assertFalse(artworkArtistsMatch("Arijit Singh", "Anuv Jain"))
    }

    @Test
    fun `youtube thumbnails are promoted to the largest available rendition`() {
        val url = "https://i.ytimg.com/vi/GX9x62kFsVU/hqdefault.jpg?sqp=abc"
        assertEquals(
            "https://i.ytimg.com/vi/GX9x62kFsVU/maxresdefault.jpg?sqp=abc",
            highResolutionArtworkUrl(url),
        )
        assertEquals(
            "https://i.ytimg.com/vi/GX9x62kFsVU/mqdefault.jpg?sqp=abc",
            artworkColorSourceUrl(url),
        )
    }

    @Test
    fun `google artwork sizes are promoted for display and reduced for palette sampling`() {
        val url = "https://lh3.googleusercontent.com/cover=w544-h544-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/cover=w1200-h1200",
            highResolutionArtworkUrl(url),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/cover=w256-h256",
            artworkColorSourceUrl(url),
        )
    }
}
