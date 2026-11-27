package com.revibe.personalization

import com.revibe.model.UserActivitySnapshot

/** How much real activity the user has, never account age. */
enum class UserMaturity { NEW, EARLY, ACTIVE, ESTABLISHED }

/**
 * Pure classifier over a real SQLite activity snapshot. Thresholds are
 * deliberately explicit and unit-tested so the homepage cannot silently become
 * personalised for a user with no data.
 */
object UserMaturityService {
    fun classify(snapshot: UserActivitySnapshot): UserMaturity {
        val noMeaningfulSignals =
            snapshot.playCount < 5 &&
                snapshot.likedTrackCount < 3 &&
                snapshot.playlistCount == 0 &&
                snapshot.followedArtistCount == 0 &&
                snapshot.localTrackCount < 25

        if (noMeaningfulSignals) return UserMaturity.NEW

        val established =
            snapshot.playCount >= 120 &&
                snapshot.distinctArtists >= 20 &&
                snapshot.historySpanDays >= 30 &&
                (snapshot.playlistCount >= 3 || snapshot.likedTrackCount >= 25)

        if (established) return UserMaturity.ESTABLISHED

        val active =
            snapshot.playCount >= 30 &&
                snapshot.distinctArtists >= 8 &&
                (snapshot.likedTrackCount >= 5 || snapshot.playlistCount >= 2)

        if (active) return UserMaturity.ACTIVE

        return UserMaturity.EARLY
    }
}

/**
 * Concretely which sections the home page may show, in priority order. A
 * section is only listed when its data requirement is met.
 */
enum class HomepageSectionKind {
    RECENTLY_PLAYED,
    MADE_FOR_YOU,
    TOP_MIXES,
    FOLLOWED_ARTISTS,
    DISCOVERY,
}

data class HomepagePlan(
    val maturity: UserMaturity,
    val sections: List<HomepageSectionKind>,
)

object HomepageSectionManager {

    fun plan(
        snapshot: UserActivitySnapshot,
        hasRecentHistory: Boolean,
    ): HomepagePlan {
        val maturity = UserMaturityService.classify(snapshot)
        val sections = mutableListOf<HomepageSectionKind>()

        if (hasRecentHistory) sections += HomepageSectionKind.RECENTLY_PLAYED

        if (maturity != UserMaturity.NEW && snapshot.topArtists.isNotEmpty()) {
            sections += HomepageSectionKind.MADE_FOR_YOU
        }

        if (maturity == UserMaturity.ACTIVE || maturity == UserMaturity.ESTABLISHED) {
            if (snapshot.topArtists.isNotEmpty()) sections += HomepageSectionKind.TOP_MIXES
        }

        if (maturity != UserMaturity.NEW && snapshot.followedArtistCount > 0) {
            sections += HomepageSectionKind.FOLLOWED_ARTISTS
        }

        // Discovery is always available, including for a brand-new install.
        sections += HomepageSectionKind.DISCOVERY

        return HomepagePlan(maturity, sections.distinct())
    }
}
