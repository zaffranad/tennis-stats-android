package fr.zaffran.tennisstats.data.local

import androidx.room.Embedded

data class MatchWithShotCount(@Embedded val match: MatchEntity, val shotCount: Int)
