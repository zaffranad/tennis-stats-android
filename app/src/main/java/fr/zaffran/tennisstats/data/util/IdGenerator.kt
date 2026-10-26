package fr.zaffran.tennisstats.data.util

import java.util.UUID
import javax.inject.Inject

/** Generates entity identifiers, injected so that tests are deterministic. */
fun interface IdGenerator {
    fun newId(): String
}

/** Random UUID v4. */
class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
