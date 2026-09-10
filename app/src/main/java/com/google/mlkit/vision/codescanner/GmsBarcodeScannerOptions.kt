package com.google.mlkit.vision.codescanner

class GmsBarcodeScannerOptions private constructor() {
    class Builder {
        fun setBarcodeFormats(@Suppress("UNUSED_PARAMETER") formats: Int): Builder = this
        fun enableAutoZoom(): Builder = this
        fun build(): GmsBarcodeScannerOptions = GmsBarcodeScannerOptions()
    }
}
