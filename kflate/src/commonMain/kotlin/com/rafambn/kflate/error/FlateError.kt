package com.rafambn.kflate.error

/** Error reported when compressed data is invalid or exceeds a configured limit. */
class FlateError(val code: FlateErrorCode) : Exception(code.message)

internal fun createFlateError(errorCode: FlateErrorCode): Nothing {
    throw FlateError(errorCode)
}
