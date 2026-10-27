package fr.zaffran.tennisstats.domain.stats

import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType

/** What distinguishes two shots for the statistics. */
data class ShotKey(val type: ShotType, val outcome: ShotOutcome, val isSecondServe: Boolean)

/**
 * Number of shots per [ShotKey]: everything the statistics need, without the shots themselves.
 * Tallies of several matches add up, which gives pooled statistics.
 */
data class ShotTally(val counts: Map<ShotKey, Int> = emptyMap()) {

    val total: Int get() = counts.values.sum()

    fun count(predicate: (ShotKey) -> Boolean): Int = counts.entries.sumOf { (key, n) -> if (predicate(key)) n else 0 }

    operator fun plus(other: ShotTally): ShotTally {
        val merged = counts.toMutableMap()
        other.counts.forEach { (key, n) -> merged.merge(key, n, Int::plus) }
        return ShotTally(merged)
    }

    companion object {
        val EMPTY = ShotTally()

        fun of(shots: List<Shot>): ShotTally =
            ShotTally(shots.groupingBy { ShotKey(it.type, it.outcome, it.isSecondServe) }.eachCount())
    }
}

/** Sums the tallies (pooled statistics). */
fun Iterable<ShotTally>.sum(): ShotTally = fold(ShotTally.EMPTY, ShotTally::plus)

/** A completed match with the tally of its shots (history and evolution). */
data class MatchTally(val match: Match, val tally: ShotTally)
