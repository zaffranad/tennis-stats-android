package fr.zaffran.tennisstats.domain.model

/** Status of a match. [value] is the persisted value, identical to the web app. */
enum class MatchStatus(val value: String) {
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    ;

    companion object {
        fun fromValue(value: String): MatchStatus = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("Unknown match status: $value")
    }
}
