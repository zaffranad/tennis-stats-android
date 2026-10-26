package fr.zaffran.tennisstats.domain.input

import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType

/**
 * Input state machine of the ShotTracker (functional spec §8.3).
 *
 * Pure function: the caller persists the returned [ShotToRecord] and applies the new state
 * only once the write succeeded.
 */
object ShotInputReducer {

    private val GROUNDSTROKE_OUTCOMES = setOf(ShotOutcome.IN, ShotOutcome.WINNER, ShotOutcome.FAULT, ShotOutcome.NET)
    private val SERVE_OUTCOMES = setOf(ShotOutcome.IN, ShotOutcome.ACE, ShotOutcome.FAULT, ShotOutcome.NET)

    /** Returns the next state and the shot to record, if any. Invalid actions leave the state unchanged. */
    fun reduce(state: ShotInputState, action: InputAction): Pair<ShotInputState, ShotToRecord?> = when (action) {
        is InputAction.ToggleServe -> toggleServe(state, action.number) to null
        is InputAction.ToggleModifier -> toggleModifier(state, action.type) to null
        is InputAction.TapOutcome -> tapOutcome(state, action.column, action.outcome)
    }

    private fun toggleServe(state: ShotInputState, number: Int): ShotInputState = when {
        number != 1 && number != 2 -> state
        state.isServeMode && state.serveNumber == number -> state.copy(activeMode = null)
        else -> state.copy(activeMode = ShotType.SERVE, serveNumber = number)
    }

    private fun toggleModifier(state: ShotInputState, type: ShotType): ShotInputState = when {
        !type.isSpecial -> state
        state.activeMode == type -> state.copy(activeMode = null)
        else -> state.copy(activeMode = type)
    }

    private fun tapOutcome(
        state: ShotInputState,
        column: Column,
        outcome: ShotOutcome,
    ): Pair<ShotInputState, ShotToRecord?> {
        val mode = state.activeMode
        return when {
            mode == ShotType.SERVE -> serve(state, column, outcome)

            outcome !in GROUNDSTROKE_OUTCOMES -> state to null

            mode != null -> state.copy(activeMode = null) to ShotToRecord(mode, outcome, isSecondServe = false)

            else -> {
                val type = if (column == Column.LEFT) ShotType.BACKHAND else ShotType.FOREHAND
                state to ShotToRecord(type, outcome, isSecondServe = false)
            }
        }
    }

    private fun serve(
        state: ShotInputState,
        column: Column,
        outcome: ShotOutcome,
    ): Pair<ShotInputState, ShotToRecord?> {
        // The left column is disabled in serve mode.
        if (column == Column.LEFT || outcome !in SERVE_OUTCOMES) return state to null
        return if (state.serveNumber == 1) {
            val shot = ShotToRecord(ShotType.SERVE, outcome, isSecondServe = false)
            if (outcome.isMiss) {
                ShotInputState(activeMode = ShotType.SERVE, serveNumber = 2) to shot
            } else {
                ShotInputState(activeMode = null, serveNumber = 1) to shot
            }
        } else {
            ShotInputState(activeMode = null, serveNumber = 1) to
                ShotToRecord(ShotType.SERVE, outcome, isSecondServe = true)
        }
    }
}

/**
 * Serve number derived from the database: 2 if the last serve of the match is a first serve
 * that missed (Out or Filet), 1 otherwise.
 */
fun deriveServeNumber(lastServe: Shot?): Int =
    if (lastServe != null && lastServe.type == ShotType.SERVE && !lastServe.isSecondServe && lastServe.outcome.isMiss) {
        2
    } else {
        1
    }

/** Most recent serve of [shotsNewestFirst] (shots ordered from the most recent), or null. */
fun lastServeOf(shotsNewestFirst: List<Shot>): Shot? = shotsNewestFirst.firstOrNull { it.type == ShotType.SERVE }
