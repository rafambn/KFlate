package com.rafambn.kflate.error

/** Exception thrown when compressed data is invalid or violates a configured safety limit. */
class FlateError(val code: FlateErrorCode) : Exception(code.message)

internal fun createFlateError(errorCode: FlateErrorCode): Nothing {
    throw FlateError(errorCode)
}
