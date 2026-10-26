package fr.zaffran.tennisstats.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** Home screen: active match card and history. */
@Serializable
data object Home

/** Match creation form. */
@Serializable
data object NewMatch

/** Match screen, opened on [initialTab]. */
@Serializable
data class Match(val matchId: String, val initialTab: MatchTab)

/** Tabs of the match screen (kept: its serializer is looked up by navigation in minified builds). */
@Keep
@Serializable
enum class MatchTab {
    /** « Saisie ». */
    INPUT,

    /** « Statistiques ». */
    STATS,
}
