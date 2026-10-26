package fr.zaffran.tennisstats.domain.model

/** A match with its number of recorded shots (home screen). */
data class MatchSummary(val match: Match, val shotCount: Int)
