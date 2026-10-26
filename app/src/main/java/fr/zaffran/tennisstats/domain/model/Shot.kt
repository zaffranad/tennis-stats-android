package fr.zaffran.tennisstats.domain.model

import java.time.Instant

data class Shot(
    val id: String,
    val matchId: String,
    val type: ShotType,
    val outcome: ShotOutcome,
    /** True only for a second serve. */
    val isSecondServe: Boolean,
    val createdAt: Instant,
) {
    /** A second serve that missed (Out or Filet). */
    val isDoubleFault: Boolean get() = type == ShotType.SERVE && isSecondServe && outcome.isMiss
}
