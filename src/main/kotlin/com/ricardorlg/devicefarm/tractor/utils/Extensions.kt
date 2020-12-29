package com.ricardorlg.devicefarm.tractor.utils

suspend fun <T, R> T?.fold(ifNone: suspend () -> R, ifPresent: suspend (T) -> R): R {
    return if (this == null) {
        ifNone()
    } else
        ifPresent(this)
}