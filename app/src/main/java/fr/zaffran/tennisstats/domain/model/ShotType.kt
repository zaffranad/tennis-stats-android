package fr.zaffran.tennisstats.domain.model

/** Type of a shot. [value] is the persisted value, identical to the web app. */
enum class ShotType(val value: String) {
    SERVE("serve"),
    FOREHAND("forehand"),
    BACKHAND("backhand"),
    VOLLEY("volley"),
    SMASH("smash"),
    LOB("lob"),
    DROP_SHOT("drop_shot"),
    ;

    /** True for the special shots selected through a modifier (Volée, Smash, Lob, Amorti). */
    val isSpecial: Boolean get() = this in SPECIAL_SHOTS

    companion object {
        /** Special shots, in display order. */
        val SPECIAL_SHOTS = listOf(VOLLEY, SMASH, LOB, DROP_SHOT)

        fun fromValue(value: String): ShotType = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("Unknown shot type: $value")
    }
}
