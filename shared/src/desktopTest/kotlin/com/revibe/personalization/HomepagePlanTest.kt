package com.revibe.personalization

import com.revibe.model.ArtistAffinity
import com.revibe.model.UserActivitySnapshot
import com.revibe.platform.relativeTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomepagePlanTest {

    private fun snapshot(
        plays: Int = 0,
        artists: Int = 0,
        liked: Int = 0,
        playlists: Int = 0,
        followed: Int = 0,
        local: Int = 0,
        spanDays: Int = 0,
        topArtists: List<ArtistAffinity> = emptyList(),
    ) = UserActivitySnapshot(
        playCount = plays,
        distinctArtists = artists,
        likedTrackCount = liked,
        playlistCount = playlists,
        followedArtistCount = followed,
        localTrackCount = local,
        historySpanDays = spanDays,
        topArtists = topArtists,
    )

    @Test
    fun `empty activity is a new user`() {
        assertEquals(UserMaturity.NEW, UserMaturityService.classify(snapshot()))
    }

    @Test
    fun `imported library is not treated as an empty account`() {
        val imported = snapshot(playlists = 2, local = 500)
        assertEquals(UserMaturity.EARLY, UserMaturityService.classify(imported))
    }

    @Test
    fun `a few plays is early, repeated listening is active`() {
        assertEquals(
            UserMaturity.EARLY,
            UserMaturityService.classify(snapshot(plays = 10, artists = 6, liked = 3)),
        )
        assertEquals(
            UserMaturity.ACTIVE,
            UserMaturityService.classify(snapshot(plays = 40, artists = 10, liked = 6)),
        )
    }

    @Test
    fun `long history with strong preferences is established`() {
        val established = snapshot(
            plays = 150,
            artists = 25,
            liked = 30,
            playlists = 3,
            spanDays = 60,
            topArtists = listOf(ArtistAffinity("Arijit Singh", 30)),
        )
        assertEquals(UserMaturity.ESTABLISHED, UserMaturityService.classify(established))
    }

    @Test
    fun `new user plan is discovery only with no getting started card`() {
        val plan = HomepageSectionManager.plan(snapshot(), hasRecentHistory = false)
        assertEquals(UserMaturity.NEW, plan.maturity)
        assertEquals(listOf(HomepageSectionKind.DISCOVERY), plan.sections)
        assertFalse(HomepageSectionKind.MADE_FOR_YOU in plan.sections)
    }

    @Test
    fun `playlist count alone does not invent a personalised section`() {
        val withPlaylists = HomepageSectionManager.plan(
            snapshot(playlists = 2),
            hasRecentHistory = false,
        )
        assertFalse(HomepageSectionKind.MADE_FOR_YOU in withPlaylists.sections)
        assertFalse(HomepageSectionKind.RECENTLY_PLAYED in withPlaylists.sections)
        assertEquals(HomepageSectionKind.DISCOVERY, withPlaylists.sections.last())
    }

    @Test
    fun `established user gets mixes and recent history`() {
        val plan = HomepageSectionManager.plan(
            snapshot(
                plays = 150,
                artists = 25,
                liked = 30,
                playlists = 3,
                spanDays = 60,
                topArtists = listOf(ArtistAffinity("Arijit Singh", 30)),
            ),
            hasRecentHistory = true,
        )
        assertTrue(HomepageSectionKind.MADE_FOR_YOU in plan.sections)
        assertTrue(HomepageSectionKind.TOP_MIXES in plan.sections)
        assertTrue(HomepageSectionKind.RECENTLY_PLAYED in plan.sections)
    }

    @Test
    fun `relative time labels use real elapsed time`() {
        val now = 1_000_000_000L
        assertEquals("Just now", relativeTime(now - 10_000, now))
        assertEquals("5m ago", relativeTime(now - 5 * 60_000, now))
        assertEquals("3h ago", relativeTime(now - 3 * 3_600_000, now))
        assertEquals("2d ago", relativeTime(now - 2 * 86_400_000, now))
    }
}
