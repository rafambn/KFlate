@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

internal fun ByteArray?.toUByteArray(): UByteArray? =
    this?.let { UByteArray(it.size) { i -> it[i].toUByte() } }

internal fun UByteArray?.toByteArray(): ByteArray? =
    this?.let { ByteArray(it.size) { i -> it[i].toByte() } }
