package fr.zaffran.tennisstats.domain.model

/** Final result of a completed match. [value] is the persisted value, identical to the web app. */
enum class MatchResult(val value: String) {
    WIN("win"),
    LOSS("loss"),
    ;

    companion object {
        fun fromValue(value: String): MatchResult = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("Unknown match result: $value")
    }
}
