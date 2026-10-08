# Expiry — Product Data Federation

## Objective

Resolve a scanned barcode into a normalized product record, with particular coverage for food, cosmetics, medicines, pet food and general consumer products.

## Runtime source order

1. Expiry local catalog.
2. AEMPS CIMA in parallel for Spanish medicine barcode shapes.
3. Open Facts universal endpoint.
4. Open Food Facts / Open Beauty Facts / Open Pet Food Facts / Open Products Facts as specialised fallbacks and enrichment.
5. Optional credentialed providers through a future backend proxy.

Results are merged field-by-field. The source with the strongest identity confidence is retained as the primary provenance while missing attributes can be filled by another source.

## Medicine strategy

Spanish medicine packaging can use GTIN/NTIN and GS1 DataMatrix representations. Expiry extracts the national medicine code when it is encoded as AI (712), from the Spanish NTIN pattern, or from the corresponding retail EAN representation.

The extracted Código Nacional is sent to the public AEMPS CIMA REST service. CIMA exposes medicine and presentation resources and publishes product identifiers such as GTIN in its Nomenclátor.

Expiry never derives a dosage, treatment recommendation or medical conclusion from barcode data. The capture layer is product identification and expiry tracking only.

## Cosmetics strategy

Open Beauty Facts is the product catalogue. Cosmetic ingredient enrichment is a separate concern: the European Commission CosIng database is treated as an ingredient/regulatory reference rather than a barcode catalogue.

This distinction is intentional. A cosmetic product can be identified by its barcode in Open Beauty Facts while individual INCI ingredients can later be checked against CosIng.

## Sources not called directly from the APK

GS1 Verified by GS1 provides trusted GTIN/company/product verification, but its advanced API is member-based. The public website is limited and is not treated as a scrape target.

Commercial barcode catalogues such as UPCitemdb and Barcode Lookup are represented in the source registry but require credentials. Secrets must never be embedded in the Android client; these providers should be integrated through a server-side proxy when enabled.

## Data provenance

Each resolved product carries:

- source id and display name
- product type
- confidence score
- barcode
- national code and registration number when available
- brand/manufacturer
- category
- image
- quantity
- ingredients / active ingredients

The enriched record is cached locally to make repeat scans fast and to progressively build Expiry's own local catalogue.

## Next phase

Add a server-side federation endpoint so Expiry can safely use credentialed providers and bulk datasets without shipping API keys in the APK. The Android client should remain the capture and presentation layer.
