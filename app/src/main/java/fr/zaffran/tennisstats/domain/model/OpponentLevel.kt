package fr.zaffran.tennisstats.domain.model

/**
 * Opponent levels offered at match creation, in display order (functional spec §3.4).
 * [value] is the persisted value. The first five have a French label; the FFT rankings
 * are displayed as their value.
 */
enum class OpponentLevel(val value: String) {
    DEBUTANT("debutant"),
    INTERMEDIAIRE("intermediaire"),
    AVANCE("avance"),
    EXPERT("expert"),
    NC("nc"),
    RANK_40_2("40/2"),
    RANK_40_1("40/1"),
    RANK_30_5("30/5"),
    RANK_30_4("30/4"),
    RANK_30_3("30/3"),
    RANK_30_2("30/2"),
    RANK_30_1("30/1"),
    RANK_30("30"),
    RANK_15_5("15/5"),
    RANK_15_4("15/4"),
    RANK_15_3("15/3"),
    RANK_15_2("15/2"),
    RANK_15_1("15/1"),
    RANK_15("15"),
    RANK_5_6("5/6"),
    RANK_4_6("4/6"),
    RANK_3_6("3/6"),
    RANK_2_6("2/6"),
    RANK_1_6("1/6"),
    RANK_0("0"),
    RANK_MINUS_2_6("-2/6"),
    RANK_MINUS_4_6("-4/6"),
    RANK_MINUS_15("-15"),
    RANK_MINUS_30("-30"),
    ;

    companion object {
        /** Returns the level stored as [value], or null for an unknown value. */
        fun fromValue(value: String): OpponentLevel? = entries.firstOrNull { it.value == value }
    }
}
