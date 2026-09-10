package com.google.mlkit.vision.codescanner

class GmsBarcodeScannerOptions private constructor() {
    class Builder {
        fun setBarcodeFormats(vararg formats: Int): Builder = this
        fun enableAutoZoom(): Builder = this
        fun build(): GmsBarcodeScannerOptions = GmsBarcodeScannerOptions()
    }
}
