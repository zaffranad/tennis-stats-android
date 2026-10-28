package fr.zaffran.tennisstats.domain.input

import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.input.InputAction.CancelMiss
import fr.zaffran.tennisstats.domain.input.InputAction.StartMiss
import fr.zaffran.tennisstats.domain.input.InputAction.TapOutcome
import fr.zaffran.tennisstats.domain.input.InputAction.ToggleModifier
import fr.zaffran.tennisstats.domain.input.InputAction.ToggleServe
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import org.junit.Test

class ShotInputReducerTest {

    private val normal = ShotInputState()
    private val serve1 = ShotInputState(activeMode = ShotType.SERVE, serveNumber = 1)
    private val serve2 = ShotInputState(activeMode = ShotType.SERVE, serveNumber = 2)

    private fun reduce(state: ShotInputState, action: InputAction) = ShotInputReducer.reduce(state, action)

    // region Serve selector

    @Test
    fun firstServe_fromNormal_entersServeModeWithFirstServe() {
        assertThat(reduce(normal, ToggleServe(1))).isEqualTo(serve1 to null)
    }

    @Test
    fun firstServe_whenFirstServeActive_leavesServeMode() {
        assertThat(reduce(serve1, ToggleServe(1))).isEqualTo(ShotInputState(null, 1) to null)
    }

    @Test
    fun firstServe_whenSecondServeActive_switchesToFirstServe() {
        assertThat(reduce(serve2, ToggleServe(1))).isEqualTo(serve1 to null)
    }

    @Test
    fun firstServe_whenModifierActive_replacesModifier() {
        val state = ShotInputState(activeMode = ShotType.LOB, serveNumber = 2)
        assertThat(reduce(state, ToggleServe(1))).isEqualTo(serve1 to null)
    }

    @Test
    fun firstServe_whenNormalWithDerivedSecondServe_selectsFirstServe() {
        assertThat(reduce(ShotInputState(null, 2), ToggleServe(1))).isEqualTo(serve1 to null)
    }

    @Test
    fun secondServe_fromNormal_entersServeModeWithSecondServe() {
        assertThat(reduce(normal, ToggleServe(2))).isEqualTo(serve2 to null)
    }

    @Test
    fun secondServe_whenSecondServeActive_leavesServeModeKeepingNumber() {
        assertThat(reduce(serve2, ToggleServe(2))).isEqualTo(ShotInputState(null, 2) to null)
    }

    @Test
    fun secondServe_whenFirstServeActive_switchesToSecondServe() {
        assertThat(reduce(serve1, ToggleServe(2))).isEqualTo(serve2 to null)
    }

    @Test
    fun secondServe_whenModifierActive_replacesModifier() {
        val state = ShotInputState(activeMode = ShotType.VOLLEY)
        assertThat(reduce(state, ToggleServe(2))).isEqualTo(serve2 to null)
    }

    @Test
    fun invalidServeNumber_isIgnored() {
        assertThat(reduce(normal, ToggleServe(3))).isEqualTo(normal to null)
    }

    // endregion

    // region Modifiers

    @Test
    fun modifier_fromNormal_selectsModifier() {
        ShotType.SPECIAL_SHOTS.forEach { type ->
            assertThat(reduce(normal, ToggleModifier(type))).isEqualTo(ShotInputState(activeMode = type) to null)
        }
    }

    @Test
    fun modifier_whenAlreadySelected_deselects() {
        ShotType.SPECIAL_SHOTS.forEach { type ->
            assertThat(reduce(ShotInputState(activeMode = type), ToggleModifier(type))).isEqualTo(normal to null)
        }
    }

    @Test
    fun modifier_replacesAnotherModifier() {
        val state = ShotInputState(activeMode = ShotType.SMASH)
        assertThat(reduce(state, ToggleModifier(ShotType.DROP_SHOT)))
            .isEqualTo(ShotInputState(activeMode = ShotType.DROP_SHOT) to null)
    }

    @Test
    fun modifier_replacesServeModeAndKeepsServeNumber() {
        assertThat(reduce(serve2, ToggleModifier(ShotType.VOLLEY)))
            .isEqualTo(ShotInputState(activeMode = ShotType.VOLLEY, serveNumber = 2) to null)
    }

    @Test
    fun nonSpecialModifier_isIgnored() {
        assertThat(reduce(normal, ToggleModifier(ShotType.FOREHAND))).isEqualTo(normal to null)
        assertThat(reduce(normal, ToggleModifier(ShotType.SERVE))).isEqualTo(normal to null)
    }

    // endregion

    // region Normal mode

    @Test
    fun normal_leftColumn_recordsBackhandAndKeepsState() {
        listOf(ShotOutcome.IN, ShotOutcome.WINNER, ShotOutcome.FAULT, ShotOutcome.NET).forEach { outcome ->
            assertThat(reduce(normal, TapOutcome(Column.LEFT, outcome)))
                .isEqualTo(normal to ShotToRecord(ShotType.BACKHAND, outcome, isSecondServe = false))
        }
    }

    @Test
    fun normal_rightColumn_recordsForehandAndKeepsState() {
        listOf(ShotOutcome.IN, ShotOutcome.WINNER, ShotOutcome.FAULT, ShotOutcome.NET).forEach { outcome ->
            assertThat(reduce(normal, TapOutcome(Column.RIGHT, outcome)))
                .isEqualTo(normal to ShotToRecord(ShotType.FOREHAND, outcome, isSecondServe = false))
        }
    }

    @Test
    fun normal_keepsDerivedSecondServeNumber() {
        val state = ShotInputState(null, 2)
        assertThat(reduce(state, TapOutcome(Column.RIGHT, ShotOutcome.IN)).first).isEqualTo(state)
    }

    @Test
    fun normal_aceOrUnforcedError_isIgnored() {
        assertThat(reduce(normal, TapOutcome(Column.RIGHT, ShotOutcome.ACE))).isEqualTo(normal to null)
        assertThat(reduce(normal, TapOutcome(Column.LEFT, ShotOutcome.UNFORCED_ERROR))).isEqualTo(normal to null)
    }

    // endregion

    // region Modifier mode

    @Test
    fun modifier_eitherColumn_recordsSpecialShotAndResetsMode() {
        ShotType.SPECIAL_SHOTS.forEach { type ->
            Column.entries.forEach { column ->
                val state = ShotInputState(activeMode = type)
                assertThat(reduce(state, TapOutcome(column, ShotOutcome.WINNER)))
                    .isEqualTo(normal to ShotToRecord(type, ShotOutcome.WINNER, isSecondServe = false))
            }
        }
    }

    @Test
    fun modifier_keepsServeNumber() {
        val state = ShotInputState(activeMode = ShotType.SMASH, serveNumber = 2)
        assertThat(reduce(state, TapOutcome(Column.LEFT, ShotOutcome.NET)))
            .isEqualTo(ShotInputState(null, 2) to ShotToRecord(ShotType.SMASH, ShotOutcome.NET, false))
    }

    @Test
    fun modifier_aceIsIgnored() {
        val state = ShotInputState(activeMode = ShotType.LOB)
        assertThat(reduce(state, TapOutcome(Column.RIGHT, ShotOutcome.ACE))).isEqualTo(state to null)
    }

    // endregion

    // region Serve mode

    @Test
    fun firstServe_inOrAce_recordsAndReturnsToNormal() {
        listOf(ShotOutcome.IN, ShotOutcome.ACE).forEach { outcome ->
            assertThat(reduce(serve1, TapOutcome(Column.RIGHT, outcome)))
                .isEqualTo(normal to ShotToRecord(ShotType.SERVE, outcome, isSecondServe = false))
        }
    }

    @Test
    fun firstServe_faultOrNet_recordsAndMovesToSecondServe() {
        listOf(ShotOutcome.FAULT, ShotOutcome.NET).forEach { outcome ->
            assertThat(reduce(serve1, TapOutcome(Column.RIGHT, outcome)))
                .isEqualTo(serve2 to ShotToRecord(ShotType.SERVE, outcome, isSecondServe = false))
        }
    }

    @Test
    fun secondServe_anyOutcome_recordsSecondServeAndReturnsToNormal() {
        listOf(ShotOutcome.IN, ShotOutcome.ACE, ShotOutcome.FAULT, ShotOutcome.NET).forEach { outcome ->
            assertThat(reduce(serve2, TapOutcome(Column.RIGHT, outcome)))
                .isEqualTo(normal to ShotToRecord(ShotType.SERVE, outcome, isSecondServe = true))
        }
    }

    @Test
    fun serveMode_leftColumn_isIgnored() {
        listOf(serve1, serve2).forEach { state ->
            ShotOutcome.entries.forEach { outcome ->
                assertThat(reduce(state, TapOutcome(Column.LEFT, outcome))).isEqualTo(state to null)
            }
        }
    }

    @Test
    fun serveMode_winnerOrUnforcedError_isIgnored() {
        assertThat(reduce(serve1, TapOutcome(Column.RIGHT, ShotOutcome.WINNER))).isEqualTo(serve1 to null)
        assertThat(reduce(serve2, TapOutcome(Column.RIGHT, ShotOutcome.UNFORCED_ERROR))).isEqualTo(serve2 to null)
    }

    @Test
    fun sequence_fault_thenDoubleFault_thenBackToNormal() {
        val (afterFirst, first) = reduce(
            normal.let {
                reduce(it, ToggleServe(1)).first
            },
            TapOutcome(Column.RIGHT, ShotOutcome.FAULT),
        )
        assertThat(first).isEqualTo(ShotToRecord(ShotType.SERVE, ShotOutcome.FAULT, isSecondServe = false))
        assertThat(afterFirst).isEqualTo(serve2)

        val (afterSecond, second) = reduce(afterFirst, TapOutcome(Column.RIGHT, ShotOutcome.NET))
        assertThat(second).isEqualTo(ShotToRecord(ShotType.SERVE, ShotOutcome.NET, isSecondServe = true))
        assertThat(afterSecond).isEqualTo(normal)

        val (afterGroundstroke, third) = reduce(afterSecond, TapOutcome(Column.LEFT, ShotOutcome.IN))
        assertThat(third).isEqualTo(ShotToRecord(ShotType.BACKHAND, ShotOutcome.IN, isSecondServe = false))
        assertThat(afterGroundstroke).isEqualTo(normal)
    }

    // endregion

    @Test(expected = IllegalArgumentException::class)
    fun invalidStateIsRejected() {
        ShotInputState(activeMode = ShotType.FOREHAND)
    }

    // region « Raté » (pending miss)

    @Test
    fun startMiss_waitsForQualificationWithoutRecording() {
        assertThat(reduce(normal, StartMiss(Column.LEFT))).isEqualTo(normal.copy(pendingMiss = Column.LEFT) to null)
    }

    @Test
    fun pendingMiss_outOrNet_recordsMissOfThatColumn() {
        val pending = normal.copy(pendingMiss = Column.LEFT)
        assertThat(reduce(pending, TapOutcome(Column.LEFT, ShotOutcome.NET)))
            .isEqualTo(normal to ShotToRecord(ShotType.BACKHAND, ShotOutcome.NET, isSecondServe = false))
    }

    @Test
    fun pendingMiss_withModifier_recordsSpecialShotAndResetsMode() {
        val pending = ShotInputState(activeMode = ShotType.SMASH, pendingMiss = Column.RIGHT)
        assertThat(reduce(pending, TapOutcome(Column.RIGHT, ShotOutcome.FAULT)))
            .isEqualTo(normal to ShotToRecord(ShotType.SMASH, ShotOutcome.FAULT, isSecondServe = false))
    }

    @Test
    fun pendingMiss_onFirstServe_recordsFaultAndMovesToSecondServe() {
        val pending = serve1.copy(pendingMiss = Column.RIGHT)
        assertThat(reduce(pending, TapOutcome(Column.RIGHT, ShotOutcome.FAULT)))
            .isEqualTo(serve2 to ShotToRecord(ShotType.SERVE, ShotOutcome.FAULT, isSecondServe = false))
    }

    @Test
    fun pendingMiss_ignoresEverythingButItsQualificationAndCancel() {
        val pending = ShotInputState(activeMode = ShotType.LOB, pendingMiss = Column.RIGHT)
        listOf(
            TapOutcome(Column.RIGHT, ShotOutcome.IN),
            TapOutcome(Column.LEFT, ShotOutcome.NET),
            StartMiss(Column.LEFT),
            ToggleServe(1),
            ToggleModifier(ShotType.VOLLEY),
        ).forEach { action ->
            assertThat(reduce(pending, action)).isEqualTo(pending to null)
        }
    }

    @Test
    fun cancelMiss_restoresStateWithoutRecording() {
        val pending = serve2.copy(pendingMiss = Column.RIGHT)
        assertThat(reduce(pending, CancelMiss)).isEqualTo(serve2 to null)
        assertThat(reduce(normal, CancelMiss)).isEqualTo(normal to null)
    }

    @Test
    fun startMiss_onLeftColumnInServeMode_isIgnored() {
        assertThat(reduce(serve1, StartMiss(Column.LEFT))).isEqualTo(serve1 to null)
    }

    // endregion
}
