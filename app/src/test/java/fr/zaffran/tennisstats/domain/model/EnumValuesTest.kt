package fr.zaffran.tennisstats.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Persisted values must stay identical to the web app. */
class EnumValuesTest {

    @Test
    fun shotTypeValues() {
        assertThat(ShotType.entries.map { it.value })
            .containsExactly("serve", "forehand", "backhand", "volley", "smash", "lob", "drop_shot")
            .inOrder()
        ShotType.entries.forEach { assertThat(ShotType.fromValue(it.value)).isEqualTo(it) }
    }

    @Test
    fun shotOutcomeValues() {
        assertThat(ShotOutcome.entries.map { it.value })
            .containsExactly("in", "winner", "ace", "fault", "net", "unforced_error")
            .inOrder()
        ShotOutcome.entries.forEach { assertThat(ShotOutcome.fromValue(it.value)).isEqualTo(it) }
    }

    @Test
    fun matchStatusAndResultValues() {
        assertThat(MatchStatus.entries.map { it.value }).containsExactly("in_progress", "completed").inOrder()
        assertThat(MatchResult.entries.map { it.value }).containsExactly("win", "loss").inOrder()
        assertThat(MatchStatus.fromValue("completed")).isEqualTo(MatchStatus.COMPLETED)
        assertThat(MatchResult.fromValue("loss")).isEqualTo(MatchResult.LOSS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownShotTypeThrows() {
        ShotType.fromValue("tweener")
    }

    @Test
    fun opponentLevelsInDisplayOrder() {
        assertThat(OpponentLevel.entries.map { it.value }).containsExactly(
            "debutant", "intermediaire", "avance", "expert", "nc",
            "40/2", "40/1", "30/5", "30/4", "30/3", "30/2", "30/1", "30",
            "15/5", "15/4", "15/3", "15/2", "15/1", "15",
            "5/6", "4/6", "3/6", "2/6", "1/6", "0", "-2/6", "-4/6", "-15", "-30",
        ).inOrder()
        assertThat(OpponentLevel.fromValue("15/4")).isEqualTo(OpponentLevel.RANK_15_4)
        assertThat(OpponentLevel.fromValue("inconnu")).isNull()
    }

    @Test
    fun specialShotsInDisplayOrder() {
        assertThat(ShotType.SPECIAL_SHOTS)
            .containsExactly(ShotType.VOLLEY, ShotType.SMASH, ShotType.LOB, ShotType.DROP_SHOT)
            .inOrder()
        assertThat(ShotType.SERVE.isSpecial).isFalse()
        assertThat(ShotType.FOREHAND.isSpecial).isFalse()
    }

    @Test
    fun doubleFaultIsSecondServeMiss() {
        val base = Shot("id", "m", ShotType.SERVE, ShotOutcome.NET, isSecondServe = true, java.time.Instant.EPOCH)
        assertThat(base.isDoubleFault).isTrue()
        assertThat(base.copy(outcome = ShotOutcome.FAULT).isDoubleFault).isTrue()
        assertThat(base.copy(outcome = ShotOutcome.IN).isDoubleFault).isFalse()
        assertThat(base.copy(isSecondServe = false).isDoubleFault).isFalse()
    }
}
