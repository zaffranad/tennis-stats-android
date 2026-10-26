package fr.zaffran.tennisstats.domain.model

/** Result of a shot. [value] is the persisted value, identical to the web app. */
enum class ShotOutcome(val value: String) {
    IN("in"),
    WINNER("winner"),
    ACE("ace"),
    FAULT("fault"),
    NET("net"),

    /** Kept for compatibility with the web model; never produced by the input UI. */
    UNFORCED_ERROR("unforced_error"),
    ;

    /** True for the missed outcomes that make a serve a fault (Out or Filet). */
    val isMiss: Boolean get() = this == FAULT || this == NET

    companion object {
        fun fromValue(value: String): ShotOutcome = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("Unknown shot outcome: $value")
    }
}
