package fr.zaffran.tennisstats.domain.model

import java.time.Instant

data class Match(
    val id: String,
    val opponentName: String,
    /** Stored level value (see [OpponentLevel]); may be a value unknown to this version. */
    val opponentLevel: String?,
    val result: MatchResult?,
    val status: MatchStatus,
    val playedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isInProgress: Boolean get() = status == MatchStatus.IN_PROGRESS
}
