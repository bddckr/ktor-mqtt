package de.kempmobil.ktor.mqtt

import kotlin.contracts.contract

/**
 * Throws a [MalformedPacketException] when `condition` is `false`, with the specified message as the exception message.
 */
internal inline fun wellFormedWhen(condition: Boolean, lazyMessage: () -> Any) {
    contract {
        returns() implies condition
    }
    if (!condition) {
        val message = lazyMessage()
        throw MalformedPacketException(message.toString())
    }
}

internal inline fun malformedWhen(condition: Boolean, lazyMessage: () -> Any) {
    contract {
        returns() implies !condition
    }
    if (condition) {
        val message = lazyMessage()
        throw MalformedPacketException(message.toString())
    }
}
