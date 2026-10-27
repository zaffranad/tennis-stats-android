package fr.zaffran.tennisstats.data.local

/** Number of shots of a match sharing the same type, outcome and serve number. */
data class ShotCountRow(
    val matchId: String,
    val type: String,
    val outcome: String,
    val isSecondServe: Boolean,
    val count: Int,
)
