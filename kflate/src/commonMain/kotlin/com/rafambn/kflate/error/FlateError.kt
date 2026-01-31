package com.rafambn.kflate.error

class FlateError(val code: FlateErrorCode) : Exception(code.message)

fun createFlateError(errorCode: FlateErrorCode): Nothing {
    throw FlateError(errorCode)
}
