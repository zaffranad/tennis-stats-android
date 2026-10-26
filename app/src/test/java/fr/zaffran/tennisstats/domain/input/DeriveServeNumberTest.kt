package fr.zaffran.tennisstats.domain.input

import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.testing.firstServe
import fr.zaffran.tennisstats.testing.secondServe
import fr.zaffran.tennisstats.testing.shot
import org.junit.Test

class DeriveServeNumberTest {

    @Test
    fun noServe_isFirstServe() {
        assertThat(deriveServeNumber(null)).isEqualTo(1)
    }

    @Test
    fun lastIsFirstServeFault_isSecondServe() {
        assertThat(deriveServeNumber(firstServe(ShotOutcome.FAULT))).isEqualTo(2)
    }

    @Test
    fun lastIsFirstServeNet_isSecondServe() {
        assertThat(deriveServeNumber(firstServe(ShotOutcome.NET))).isEqualTo(2)
    }

    @Test
    fun lastIsFirstServeIn_isFirstServe() {
        assertThat(deriveServeNumber(firstServe(ShotOutcome.IN))).isEqualTo(1)
    }

    @Test
    fun lastIsFirstServeAce_isFirstServe() {
        assertThat(deriveServeNumber(firstServe(ShotOutcome.ACE))).isEqualTo(1)
    }

    @Test
    fun lastIsSecondServeFault_isFirstServe() {
        assertThat(deriveServeNumber(secondServe(ShotOutcome.FAULT))).isEqualTo(1)
    }

    @Test
    fun lastServeOf_ignoresLaterGroundstrokes() {
        val shotsNewestFirst = listOf(
            shot(ShotType.FOREHAND, ShotOutcome.IN),
            shot(ShotType.VOLLEY, ShotOutcome.NET),
            firstServe(ShotOutcome.FAULT),
            secondServe(ShotOutcome.IN),
        )
        assertThat(lastServeOf(shotsNewestFirst)).isEqualTo(shotsNewestFirst[2])
        assertThat(deriveServeNumber(lastServeOf(shotsNewestFirst))).isEqualTo(2)
    }

    @Test
    fun lastServeOf_noServe_isNull() {
        assertThat(lastServeOf(listOf(shot(ShotType.BACKHAND, ShotOutcome.IN)))).isNull()
        assertThat(lastServeOf(emptyList())).isNull()
    }
}
