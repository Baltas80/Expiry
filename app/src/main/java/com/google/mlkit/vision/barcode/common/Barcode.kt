package com.google.mlkit.vision.barcode.common

/** Minimal compatibility model used by Expiry's scanner adapter. */
class Barcode internal constructor(val rawValue: String?) {
    companion object {
        const val FORMAT_ALL_FORMATS: Int = 0
    }
}
