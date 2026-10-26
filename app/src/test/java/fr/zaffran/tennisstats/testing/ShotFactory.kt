package fr.zaffran.tennisstats.testing

import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import java.time.Instant

private var nextId = 0

fun shot(
    type: ShotType,
    outcome: ShotOutcome,
    isSecondServe: Boolean = false,
    matchId: String = "match",
    createdAt: Instant = Instant.EPOCH,
) = Shot(
    id = "shot-${nextId++}",
    matchId = matchId,
    type = type,
    outcome = outcome,
    isSecondServe = isSecondServe,
    createdAt = createdAt,
)

fun firstServe(outcome: ShotOutcome) = shot(ShotType.SERVE, outcome, isSecondServe = false)

fun secondServe(outcome: ShotOutcome) = shot(ShotType.SERVE, outcome, isSecondServe = true)
