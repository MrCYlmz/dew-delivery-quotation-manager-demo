# Module contracts

**Authority:** `SPEC.md` is the requirements source of truth. This file fixes the seams between modules: the parser API and the REST API. Where this file and SPEC.md disagree, SPEC.md wins on behaviour and this file wins on signatures; the disagreement is a point for the next story, not something to resolve in code.

This file changes only through an approved story.

---

## 1. Module boundaries

| Module | Holds | Depends on |
|---|---|---|
| `parser/` | Excel parsing with Apache POI, SPEC.md §8, §11, §12. A plain Java library. | nothing in this repo |
| `backend/` | Spring Boot, SQLite, Flyway, REST, file storage. Serves the built frontend. | `parser/` through the API in §2 |
| `frontend/` | Vue 3 + TS, the screens of SPEC.md §9. | `backend/` through the REST API in §3 |

---

## 2. Parser API: the parser publishes, the backend consumes

Package `de.dew.parser`. Maven coordinates `de.dew:parser` (version managed by the parent pom).

```java
public interface PackingListParser {
    /** @throws PackingListParseException when no packing list sheet is found (SPEC.md 8.1 rule 3)
     *          or a required column cannot be mapped (SPEC.md 8.3). Never falls back to sheet 0. */
    PackingListResult parse(InputStream in, String originalFilename) throws PackingListParseException;
}

/** Totals are the parser's calculated sums. The backend may overwrite them with Alice's
 *  edits before persisting (SPEC.md F-8); the parser never sees those edits. */
public record PackingListResult(
    BigDecimal totalVolumeM3,
    BigDecimal totalGrossWeightKg,
    BigDecimal totalPalletCount,
    List<PackingListItem> items,
    int volumeMismatchCount,
    String sheetName
) {}

public record PackingListItem(
    int sourceRow,
    String description,      // carried forward on continuation rows (SPEC.md 8.4)
    String articleNo,
    BigDecimal lengthMm,
    BigDecimal widthMm,
    BigDecimal heightMm,
    BigDecimal palletCount,
    BigDecimal volumeM3,
    BigDecimal grossWeightKg,
    BigDecimal netWeightKg,
    boolean volumeMismatch   // set per SPEC.md F-16 / 8.7, threshold 0.01 m3
) {}

public class PackingListParseException extends Exception {
    public PackingListParseException(String message) { ... }
}
```

Nulls: any `BigDecimal` on `PackingListItem` may be `null` when the source cell is blank — blank is *not* coerced to zero at the item level (SPEC.md §8.5 says blank counts as 0 **for summing**, which is the parser's business, not the item table's). `description` may be `null` only if no prior row supplied one. The four `PackingListResult` totals are never `null`.

**The implementation class name is part of the seam.** The implementation is
`de.dew.parser.DefaultPackingListParser`, with a public no-arg constructor. The backend wires
that exact class as a `@Bean`, so renaming it breaks the backend.

The parser is a **plain Java library**: no Spring, no annotations, no database, no file-system writes, no logging framework beyond SLF4J API.

---

## 3. REST API: the backend publishes, the frontend consumes

Base path `/api`. JSON, `snake_case` field names matching SPEC.md §7 exactly. Dates are ISO-8601 `yyyy-MM-dd`. Money and cargo figures are JSON numbers, not strings. Dev server on port **8080**.

### Deliveries

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/deliveries` | List + filter + sort (F-11, F-12, F-14) |
| `POST` | `/api/deliveries` | Create (F-1) |
| `GET` | `/api/deliveries/{id}` | Detail incl. items + pdfs (F-4) |
| `PUT` | `/api/deliveries/{id}` | Update (F-2) |
| `DELETE` | `/api/deliveries/{id}` | Delete, cascades items + pdfs + files on disk (F-3) |
| `GET` | `/api/board` | The board's cards (F-27, F-28, F-30) |
| `PUT` | `/api/deliveries/{id}/status` | Move to a status, with the dates the dialog asked for (F-29) |

`GET /api/deliveries` query params — all optional, all AND-combined (F-13):

```
customer_company, origin_country, origin_city,
destination_country, destination_city, transport_method,
shipping_date_from, shipping_date_to,
arrival_date_from, arrival_date_to,
cost_min, cost_max,
status   repeatable: status=TO_DO&status=ON_THE_WAY matches any of them;
         omitted or blank shows every status (F-12)
sort   = shipping_date | arrival_date | cost_eur | volume_m3 | gross_weight_kg
order  = asc | desc                       (default: shipping_date desc, F-11)
```

A `null` in the sort column sorts last in either order. While `cost_min` or `cost_max` is set, a delivery without `cost_eur` is left out. An unknown `status` value is a `400`.

List row shape — deliberately lighter than the detail; no items, no pdf list:

```json
{
  "id": 1,
  "customer_company": "Example Interiors",
  "origin_country": "CN", "origin_city": "Ningbo",
  "destination_country": "DE", "destination_city": "Munich",
  "transport_method": "SEA",
  "status": "DELIVERED",
  "shipping_date": "2026-01-15", "arrival_date": "2026-03-02",
  "cost_eur": 4210.00,
  "volume_m3": 7.42, "gross_weight_kg": 1580, "pallet_count": 6,
  "has_notes": true,
  "pdf_count": 3
}
```

`has_notes` exists so the list can mark rows without shipping note bodies (F-18). `transport_method` is the enum `SEA | AIR | ROAD | RAIL` on the wire — exactly four, no "OTHER" (SPEC.md §7, OQ-3). `status` is the enum `TO_DO | OFFERS_RECEIVED | OFFER_ACCEPTED | ON_THE_WAY | DELIVERED`, in the board's order (F-24). `shipping_date`, `arrival_date` and `cost_eur` may be `null`. `arrival_date` is the real arrival (F-25) everywhere in this API; the list's `arrival_date_from`, `arrival_date_to` and `sort=arrival_date` work on it.

Detail adds: `origin_postal_code`, `origin_street`, `destination_postal_code`, `destination_street`, `notes`, `excel_filename`, `offer_price_eur` (or `null`), `expected_arrival_date` (date or `null`, F-25), `moved_to_delivered_on` (date or `null`, F-27), plus:

```json
"totals_edited": { "volume_m3": true, "gross_weight_kg": false, "pallet_count": false },
"item_sums":     { "volume_m3": 7.42, "gross_weight_kg": 1580, "pallet_count": 6 },
"items": [ { "source_row": 5, "description": "...", "article_no": "...",
             "length_mm": 1200, "width_mm": 800, "height_mm": 1050,
             "pallet_count": 2, "volume_m3": 2.02,
             "gross_weight_kg": 480, "net_weight_kg": 395.5,
             "volume_mismatch": false } ],
"pdfs": [ { "id": 7, "original_filename": "QX20260115 delivery note.pdf",
            "size_bytes": 284119, "sort_order": 0,
            "uploaded_at": "2026-07-05T09:12:00Z" } ]
```

**Replacing the Excel never rewrites Alice's saved totals.** F-8 says saved totals are authoritative; silently resetting them to the freshly parsed sums would destroy an override she made deliberately. `PUT .../excel` swaps the file and the line items, leaves `volume_m3` / `gross_weight_kg` / `pallet_count` on the delivery alone, and lets the new sums surface through `item_sums` — where F-10 will mark the divergence and show both. Alice then accepts or re-overrides them herself.

When no Excel has ever been uploaded, `item_sums` is `null` and every `totals_edited` value is `false`.

`totals_edited` and `item_sums` are what F-10 renders — the backend computes them by comparing the saved total against the sum of stored items. The frontend does not recompute sums. The offer/real difference of F-4 is display arithmetic on `offer_price_eur` and `cost_eur`, and the arrival difference in days on `expected_arrival_date` and `arrival_date`; the frontend computes both.

**Create and update body** (`POST`, `PUT /api/deliveries/{id}`): the detail's editable fields, now including `status` (required), `offer_price_eur` (optional) and `expected_arrival_date` (optional at every status); `cost_eur` is optional; a negative `offer_price_eur` is a `400`, as for `cost_eur`. `shipping_date` is required when `status` is `ON_THE_WAY` or `DELIVERED`, `arrival_date` when it is `DELIVERED` (F-25), else `400` with `{"shipping_date": "required"}` and/or `{"arrival_date": "required"}` in `fields`. `moved_to_delivered_on` is never sent; the backend sets it from the injected `Clock` when an existing delivery's `status` changes to `DELIVERED` (a delivery created at `DELIVERED` gets none), clears it when `status` changes away, and keeps it otherwise (F-27).

### Board

```
GET /api/board
```

Read-only. Every delivery not `DELIVERED`, plus `DELIVERED` ones with `moved_to_delivered_on` from today − 6 days to today (F-27). One flat array, ordered by `updated_at` descending, then `id` descending; the frontend groups it into columns by `status` and keeps that order within each.

```json
[
  { "id": 51,
    "status": "ON_THE_WAY",
    "customer_company": "Example Interiors",
    "origin_country": "CN", "origin_city": "Ningbo",
    "destination_country": "DE", "destination_city": "Munich",
    "transport_method": "SEA",
    "shipping_date": "2026-10-20",
    "expected_arrival_date": "2026-10-28", "arrival_date": null,
    "days_late": 3,
    "offer_price_eur": 3900.00 }
]
```

`shipping_date`, `expected_arrival_date`, `arrival_date` and `offer_price_eur` may be `null`. `days_late` is today (the injected `Clock`) minus `expected_arrival_date` in days when `status` is `ON_THE_WAY`, `expected_arrival_date` is set and that difference is at least 1; else `null` (F-30). The colour level for a `days_late` is the frontend's. The F-26 hint is the frontend's: a card at `OFFER_ACCEPTED`, `ON_THE_WAY` or `DELIVERED` with `offer_price_eur` `null`.

```
PUT /api/deliveries/{id}/status
{ "status": "DELIVERED", "shipping_date": "2026-10-20", "arrival_date": "2026-11-30" }
```

Moves the delivery to `status` (required). `shipping_date`, `expected_arrival_date` and `arrival_date` are optional: a date sent replaces the stored one, a date left out or `null` keeps it, so a move never clears a date (F-25, F-29). The resulting delivery must satisfy F-25, else `400` naming the missing dates in `fields`, and nothing changes. Sets, clears or keeps `moved_to_delivered_on` as for an update, and bumps `updated_at`. Moving to the current status is allowed and only saves the dates sent. Returns the board card shape above; `404` for an unknown id.

### Files

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/deliveries/{id}/pdfs` | `multipart/form-data`, field `files`, **repeatable** — several PDFs in one request (F-5) |
| `DELETE` | `/api/deliveries/{id}/pdfs/{pdfId}` | Removes row *and* file on disk |
| `GET` | `/api/deliveries/{id}/pdfs/{pdfId}/content` | `Content-Type: application/pdf`, `inline` — feeds the `<iframe>` viewer |
| `PUT` | `/api/deliveries/{id}/excel` | `multipart/form-data`, field `file`. Replaces the file, re-parses, replaces the stored line items. **Must not overwrite the delivery's saved totals** — see below. Returns the detail body |
| `GET` | `/api/deliveries/{id}/excel` | `Content-Disposition: attachment`, original filename (F-6) |

### Excel preview — parse without saving

```
POST /api/excel/parse     multipart/form-data, field `file`
```

Returns `PackingListResult` as JSON (`snake_case`), flat, with exactly these keys:

```json
{ "total_volume_m3": 7.42, "total_gross_weight_kg": 1580, "total_pallet_count": 6,
  "volume_mismatch_count": 0, "sheet_name": "PACKING LIST ",
  "items": [ { "source_row": 5, "description": "...", "article_no": "...",
               "length_mm": 1200, "width_mm": 800, "height_mm": 1050,
               "pallet_count": 2, "volume_m3": 2.02, "gross_weight_kg": 480,
               "net_weight_kg": 395.5, "volume_mismatch": false } ] }
```

Not nested under a wrapper key. No net-weight total: SPEC.md §7 is explicit that net is never summed into the delivery record. This is returned **without touching the database**. This is what SPEC.md §9 screen 2 needs: Alice uploads, sees the totals land in editable fields and the mismatch count, and only then saves. On parse failure returns `422` with `{"error": "...", "detail": "..."}` carrying the parser's message — §8.1/§8.3 require failing loudly and naming what is missing.

### Country values — a closed list, not free text

`origin_country` and `destination_country` are **ISO 3166-1 alpha-2 codes** on the wire (`DE`, `CN`, `VN`) — which is what the examples above have always shown. SPEC.md §7 requires them to come "from a country list — normalized, so filtering is exact", and suggestions-from-existing-data cannot satisfy that: on an empty database the list is empty and the first records get typed freehand, which is the exact fragmentation §7 and F-15 exist to prevent.

The split:

- **Entry form** offers the full ISO 3166-1 list (bundled in the frontend — a closed, stable set needs no endpoint and no round-trip). Alice picks a country; the code is stored.
- **Filter bar** offers only the codes already present in the data, from `/api/suggestions/countries` (F-15: "values already present in the data, so Alice picks rather than types"). No point offering her 200 countries she has never shipped from.
- **Display** renders the country name, never the bare code. `DE` in the database, "Germany" on screen.

Cities stay free text with suggestions — there is no closed list of cities, and §7 asks for none.

### Autocomplete / filter values (F-15)

```
GET /api/suggestions/companies
GET /api/suggestions/countries?field=origin|destination
GET /api/suggestions/cities?field=origin|destination&country=DE
```

Each returns a plain `["..."]` of distinct values already in the data, sorted. `countries` returns ISO alpha-2 codes. Calling `cities` with `country` omitted returns every city for that field, unfiltered.

These populate the **filter bar**. The entry form's country field uses the bundled ISO list instead (above); its company and city fields use these endpoints.

### Estimate comparables (F-20, F-21)

```
GET /api/estimate/comparables
```

Read-only; touches nothing. Query params:

```
transport_method      required, SEA | AIR | ROAD | RAIL
origin_country        required, ISO alpha-2
destination_country   required, ISO alpha-2
volume_m3             optional, > 0
gross_weight_kg       optional, > 0
```

A missing or blank required param, a country that is not two letters, an unknown method, a non-numeric value or a value ≤ 0 is a `400` validation error. City, shipping date and pallets are not sent: they are only printed in the prompt, which the frontend builds.

```json
{
  "chargeable_kg": 5000.00,
  "fallback": "NONE",
  "removed_company_names": 3,
  "comparables": [
    { "id": 42,
      "origin_country": "CN", "origin_city": "Ningbo",
      "destination_country": "DE", "destination_city": "Munich",
      "transport_method": "SEA",
      "shipping_date": "2026-03-10",
      "expected_arrival_date": "2026-04-18", "arrival_date": "2026-04-21",
      "age_months": 7,
      "volume_m3": 4.8, "gross_weight_kg": 900, "pallet_count": 6,
      "chargeable_kg": 4800.00,
      "cost_eur": 4100.00,
      "offer_price_eur": 3900.00,
      "notes": "[customer] wanted it fast, rush premium" }
  ]
}
```

- `chargeable_kg` at the top is the potential delivery's (F-20), `null` without a size. On a comparable it is its own chargeable from its saved totals, `null` when it has no size; that can only happen when the request has no size, since otherwise such deliveries are left out.
- `fallback` is `NONE | DESTINATION_IGNORED | ORIGIN_AND_DESTINATION_IGNORED` (F-21).
- `comparables` is in rank order, at most 10, and empty when nothing matches; `fallback` is then `ORIGIN_AND_DESTINATION_IGNORED` and the frontend shows "Not enough comparable deliveries".
- Candidates exclude status `TO_DO` and deliveries with neither `cost_eur` nor `offer_price_eur` (F-21). Either price may be `null` on a returned comparable, never both; the prompt uses `cost_eur`, else `offer_price_eur` marked as an offer (F-22).
- `shipping_date`, `expected_arrival_date` and `arrival_date` may be `null`; the prompt prints `arrival_date`, else `expected_arrival_date` marked `(expected)` (F-22). `age_months` is whole months from `shipping_date` to today, rounded down; 0 when the shipping date is after today or `null`.
- `notes` comes back with company names already replaced as whole words, ignoring case, longest name first (F-22); `removed_company_names` counts the replacements over all returned notes. `notes` is `null` when the delivery has none. There is no `customer_company` field.

The frontend builds the prompt text from this body and the form (F-22); the template lives in the frontend.

### Errors

`400` validation (body `{"error":"validation","fields":{"cost_eur":"required"}}`), `404` unknown id, `422` parse failure, `500` otherwise. Never return `200` with an error payload.

---

## 4. Frontend → backend build seam

- Dev: Vite proxies `/api` to `http://localhost:8080`. There is no CORS config, because the packaged jar serves frontend and API same-origin (SPEC.md §10.1).
- Package: `npm run build` in `frontend/` outputs to `backend/src/main/resources/static/`. That path is gitignored; build output is never committed.
- `mvn package` builds the frontend through `frontend-maven-plugin` in `backend/pom.xml` (typecheck, lint, tests, then build), so one jar serves everything. `-Dskip.frontend=true` skips the npm steps.
