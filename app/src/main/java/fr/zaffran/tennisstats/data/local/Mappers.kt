package fr.zaffran.tennisstats.data.local

import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.MatchSummary
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.domain.stats.ShotKey
import fr.zaffran.tennisstats.domain.stats.ShotTally

fun MatchEntity.toDomain() = Match(
    id = id,
    opponentName = opponentName,
    opponentLevel = opponentLevel,
    result = result?.let(MatchResult::fromValue),
    status = MatchStatus.fromValue(status),
    playedAt = playedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Match.toEntity() = MatchEntity(
    id = id,
    opponentName = opponentName,
    opponentLevel = opponentLevel,
    result = result?.value,
    status = status.value,
    playedAt = playedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MatchWithShotCount.toDomain() = MatchSummary(match = match.toDomain(), shotCount = shotCount)

fun ShotEntity.toDomain() = Shot(
    id = id,
    matchId = matchId,
    type = ShotType.fromValue(type),
    outcome = ShotOutcome.fromValue(outcome),
    isSecondServe = isSecondServe,
    createdAt = createdAt,
)

fun Shot.toEntity() = ShotEntity(
    id = id,
    matchId = matchId,
    type = type.value,
    outcome = outcome.value,
    isSecondServe = isSecondServe,
    createdAt = createdAt,
)

/** Groups the rows by match into shot tallies. */
fun List<ShotCountRow>.toTallies(): Map<String, ShotTally> = groupBy { it.matchId }.mapValues { (_, rows) ->
    ShotTally(
        rows.associate {
            ShotKey(ShotType.fromValue(it.type), ShotOutcome.fromValue(it.outcome), it.isSecondServe) to
                it.count
        },
    )
}
