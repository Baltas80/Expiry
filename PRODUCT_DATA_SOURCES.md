# Expiry — Product Data Acquisition

Expiry uses a multi-source barcode acquisition layer rather than treating a single provider as authoritative.

## Active sources

| Source | Main use | Barcode path |
|---|---|---|
| Open Facts | Food, cosmetics, pet and general products | Universal \`product_type=all\` |
| AEMPS CIMA | Spanish medicines | Código Nacional / compatible Spanish GTIN |
| FDA openFDA NDC | US medicines | UPC and NDC identifiers |
| DailyMed | US drug labels / SPL | 10/11 digit NDC |
| Local catalog | Cached enrichment and offline fallback | Local barcode map |

Open Facts can resolve food, beauty, pet-food and other-product records across the sibling databases. New Open Food Facts integrations should use API v3; the implementation retains a v2 fallback for resilience.

## Cosmetics

Open Beauty Facts is the product-level cosmetic source. It contains cosmetic products and label information such as ingredients and allergens.

CosIng is **not** used as a barcode product source. It is the European Commission's reference database for cosmetic substances and ingredient names. It is therefore a separate enrichment layer to be added later, using a permitted machine-readable distribution rather than HTML scraping.

## Medicines

AEMPS CIMA is the primary Spanish medicine connector. AEMPS publishes a REST service and a medicines/presentations catalogue. The barcode normalizer understands the Spanish 7-digit Código Nacional and the documented NTIN form \`0847000 + CN\`.

openFDA NDC is the primary US structured medicine listing connector. The FDA documents UPC as a harmonized identifier when present.

DailyMed complements openFDA with Structured Product Label data. It is used conservatively because normal retail GTINs do not directly encode the NDC.

EMA's Product Management Service (PMS) public API entered beta in June 2026 with controlled registration/access. It should be integrated behind a server-side connector when Expiry has backend infrastructure; credentials or OAuth client secrets must not be placed in the Android APK.

## Resolution pipeline

1. Normalize the scanned value into deterministic barcode candidates.
2. Run every active source concurrently.
3. Isolate provider failures and timeouts.
4. Rank matches by source priority and confidence.
5. Merge non-conflicting fields from lower-priority sources.
6. Persist the enriched record into the local barcode catalog.
7. Reuse the local record on future scans.

The user-facing scanner continues to receive the existing compact \`ProductLookup.Result\`, so this acquisition layer is additive and does not require a UI migration.

## Data governance

Public reachability is not the same as permission to mirror a dataset. Before bulk import or long-term redistribution, Expiry must record the source licence and terms, attribution requirements, rate limits and image/data reuse conditions.

Open Facts documentation states that the database is available under ODbL, while individual contents and images have separate terms. Its data are contributed voluntarily and can be incomplete or inaccurate.

Medicine sources are used for identification and metadata only. Expiry must not present this data as clinical advice or a recommendation to take or use a medicine.

## Planned next sources

- CosIng ingredient enrichment for cosmetics.
- EMA PMS through a backend when access is provisioned.
- Optional commercial barcode providers behind server-side/API-key configuration.
- GS1/Verified by GS1 verification where eligible commercial/member access is available.
- Spanish manufacturer/retailer feeds for private-label products.
- Offline bulk indexes only after licence review.
