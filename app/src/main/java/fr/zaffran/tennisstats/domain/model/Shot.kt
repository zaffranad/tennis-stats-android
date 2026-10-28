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

    /** A « Réussi » shot (`in`, `winner` or `ace`): it can be qualified as a winner (RG-10). */
    val isSuccess: Boolean get() = outcome == ShotOutcome.IN || outcome == ShotOutcome.WINNER ||
        outcome == ShotOutcome.ACE

    /** True if the shot was qualified as a winner (`winner`, or `ace` for a serve). */
    val isWinner: Boolean get() = outcome == ShotOutcome.WINNER || outcome == ShotOutcome.ACE
}

/** Outcome of [shot] qualified as a winner (`ace` for a serve, `winner` otherwise) or back to `in`. */
fun winnerOutcome(shot: Shot, isWinner: Boolean): ShotOutcome = when {
    !isWinner -> ShotOutcome.IN
    shot.type == ShotType.SERVE -> ShotOutcome.ACE
    else -> ShotOutcome.WINNER
}
