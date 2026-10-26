package fr.zaffran.tennisstats.domain.input

import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType

/**
 * Transient state of the input bar (ShotTracker).
 *
 * @property activeMode null (normal), [ShotType.SERVE] or a special shot (modifier).
 * @property serveNumber 1 or 2.
 */
data class ShotInputState(val activeMode: ShotType? = null, val serveNumber: Int = 1) {
    init {
        require(serveNumber == 1 || serveNumber == 2) { "serveNumber must be 1 or 2" }
        require(activeMode == null || activeMode == ShotType.SERVE || activeMode.isSpecial) {
            "Invalid active mode: $activeMode"
        }
    }

    val isServeMode: Boolean get() = activeMode == ShotType.SERVE
}

/** Column of the input bar: left = backhand, right = forehand / serve. */
enum class Column { LEFT, RIGHT }

sealed interface InputAction {
    /** Tap on « 1er service » (1) or « 2e service » (2). */
    data class ToggleServe(val number: Int) : InputAction

    /** Tap on a modifier (Volée, Smash, Lob, Amorti). */
    data class ToggleModifier(val type: ShotType) : InputAction

    /** Tap on a result button of a column. */
    data class TapOutcome(val column: Column, val outcome: ShotOutcome) : InputAction
}

/** A shot to persist, produced by the reducer. */
data class ShotToRecord(val type: ShotType, val outcome: ShotOutcome, val isSecondServe: Boolean)
