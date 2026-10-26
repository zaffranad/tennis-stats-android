package fr.zaffran.tennisstats.data.util

import java.time.Instant
import javax.inject.Inject

/** Source of the current time, injected so that tests are deterministic. */
fun interface Clock {
    fun now(): Instant
}

class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()
}
