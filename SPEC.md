# Delivery Quotation Archive — Specification

**Status:** v9 · 2026-11-02 — generic invented fixture and examples; the article column is found without a supplier-specific label (§8.2, §11, §12) by S-11. v8 · 2026-10-31 — expected and real arrival, overdue warning on the board (F-25, F-28 to F-30) by S-10. v7 · 2026-10-30 — status board and offer price (F-24 to F-29) added by S-9. v6 · 2026-09-25 — **built.** Parser, backend and frontend implemented and merged; one runnable jar. Packaging (§10.4) still deferred. See §15.
**Owner:** Alice (sole user)
**Stack:** Vue 3 + TypeScript · Java / Spring Boot · SQLite · Apache POI — see §10
**Parser status:** validated against the packing list layout in §8; the acceptance fixture in §12 reproduces it with invented data. Layout confirmed identical across all files.

---

## 1. Problem

Alice quotes delivery costs for European companies shipping from Asian factories. Today she estimates a new quote by hunting through old emails for comparable past deliveries. Two things are slow:

1. Finding the original email for a specific past delivery.
2. Finding *all* similar past deliveries to use as a basis for a new quote.

## 2. Goal

A single place where every past delivery is recorded with its route, method, dates, cost, and cargo totals — and can be filtered down to comparable deliveries in seconds. Running deliveries are tracked there too, by status on a board, from the first request to the week after delivery (F-24 to F-29).

## 3. Non-goals (v1)

Considered and deliberately excluded:

| Excluded | Reason |
|---|---|
| Generating a quotation document/PDF | Alice writes the quote herself in email. The app is a reference archive, not a document generator. |
| Email tracking (subject/sender/`.msg` upload) | Decided the PDFs + Excel are a sufficient record. |
| Auto-filling the company from the Excel | The company is a real field (§7) but is typed by hand. The packing list's `B4` is *not* the customer name — confirmed — so nothing in the sheet is a reliable source for it. |
| Parsing the invoice sheet | Declined. An invoice's Bill To / Ship To block could plausibly have auto-filled the company and destination, but meta fields stay manual by choice. Only the packing list is read. |
| Shipment reference field | `A4` holds one (`QX20260115` in the fixture), but it isn't captured. |
| Login / accounts / permissions | Single user, runs locally. |
| Multi-currency | All costs in EUR. |
| Net weight as the headline figure | Gross weight is what carriers charge on. Net is retained per line item only (§7). |
| Automatic price prediction or suggested quotes | Alice makes the judgment call; the app surfaces comparables. The Estimate screen (F-19 to F-23) stays inside this line: it picks comparables and drafts a prompt that Alice pastes into Claude herself. The app calculates no price and calls no external service. |
| PDF data extraction | Stored for reference only — nothing is read from any of them. |
| Notes on individual PDFs or item rows | Notes attach to the delivery, not to a file or a line. A delivery's documents are told apart by their filename (§6, F-5). |
| Full-text search across notes | Notes are read on the detail view, not searched. Filtering stays structured (F-12). Cheap to add later if she asks. |
| General parse warnings | Not requested. The one exception is the volume cross-check (F-16), which fires only on a genuine contradiction in the data. |

## 4. Users

One user, Alice. No authentication, no roles, no sharing. Runs locally on her machine.

---

## 5. Core workflow

1. Email arrives from the factory with one or more delivery documents (PDFs) and a packing list (Excel).
2. Alice opens the app and clicks **New delivery**.
3. She types the meta information: European company, origin, destination, transport method, shipping date, arrival date, cost.
4. She uploads the Excel **exactly as the factory sent it** — the app finds the packing list sheet itself (§8.1). She no longer needs to delete the invoice sheet first.
5. The app extracts every item row and shows the totals — volume, gross weight, pallet count — in editable fields.
6. She uploads the delivery documents — **one PDF or several** (stored, not read). A delivery often travels with more than one: the delivery note, a bill of lading, a customs form.
7. She types any free-text notes about this delivery — what made it unusual, what the price included, anything she'd want to remember when quoting a similar one.
8. She saves. The record appears in the list.

Quoting a new delivery: she opens the list, filters by route / method / date / cost, and reads off the comparables.

Tracking a running delivery: she creates it at **To do** as soon as the request arrives, with only what she knows. On the board she moves its card as it goes: offers received, offer accepted (with the accepted offer price), on the way (with the expected arrival once the carrier gives one), delivered (with the real arrival). The board marks a delivery on the way that is past its expected arrival. The record she ends with is the same archive record as above, with the real price added once she has the invoice.

---

## 6. Functional requirements

### Delivery record

**F-1** — Create a delivery record with the fields in §7, at any status (F-24); a new delivery starts at To do.
**F-2** — Edit any field of an existing record, including its notes, replacing the Excel, and adding or removing individual PDFs.
**F-3** — Delete a record, with a confirmation step.
**F-4** — View a record in detail: meta fields, status, notes, cargo totals, item table, and the list of attached PDFs. With both an offer price and a real price, it shows the difference, real minus offer, in EUR and as a percentage of the offer, signed, in the app's money format (`+€350.00 · +9.0%`, zero as `€0.00 · 0.0%`); with an offer price of 0, the EUR amount only. It shows the expected and the real arrival; with both, the real minus the expected in days: `5 days late`, `1 day late`, `2 days early`, `on time`.

### Files

**F-5** — Upload **any number of PDFs** per delivery, in one go or added later. Each is stored as-is, listed by its original filename, and viewable and downloadable from the detail view. No extraction. Individual PDFs can be removed without touching the rest of the record.
**F-6** — Upload one Excel per delivery, parsed on upload (§8). Accepts `.xlsx` **and `.xls`** — see §10. The uploaded file is **retained and downloadable** from the detail view, same as the PDFs. It stays a single column on `Delivery` — there is exactly one Excel per record, so it does not need the child table the PDFs now use (§7).
**F-7** — All uploads are optional. A record can be saved with no PDFs and no Excel.

### Excel extraction

**F-8** — After parsing, the three totals — volume (m³), gross weight (kg), pallet count — are shown in **editable** fields, pre-filled with the calculated sums. Alice can override any before saving. Saved totals are authoritative.
**F-9** — Every parsed item row is stored and shown as a table in the detail view.
**F-10** — When saved totals differ from the sum of stored line items, the detail view marks the total as edited and shows the line-item sum alongside.
**F-16** — Per-row volume cross-check: flag any row where the stated volume disagrees with `L × W × H × pallet count` by more than **0.01 m³**. Flagged rows are highlighted in the item table and surfaced as a count during upload. They do not block saving.

### Notes

**F-17** — Each delivery carries a free-text notes field, editable on the entry form and shown in full on the detail view. Multi-line, no length limit worth enforcing, no formatting. This is where the context that no structured field captures goes — *"price included customs clearance"*, *"rush job, air freight premium"*, *"factory shipped two weeks late"* — and it is exactly that context Alice needs when she pulls up a comparable and asks why the price was what it was.

**F-18** — Notes are shown on the detail view whenever present, not hidden behind an expander. A note nobody reads is a note nobody writes. The list view (F-11) does **not** show them — it would wreck the row height — but it marks which records have one, so Alice knows a comparable is worth opening.

### Finding comparable deliveries

**F-11** — List view of all deliveries, newest shipping date first: company, origin, destination, method, status, shipping date, arrival date (the real one), cost, volume, gross weight, pallets, plus a marker on rows that carry notes (F-18). Open deliveries sit next to finished ones; a missing value shows as empty and sorts last in either order.
**F-12** — Filter by, in any combination:
- **European company**
- origin country and/or city
- destination country and/or city
- transport method
- shipping date range *and/or* arrival date range (the real arrival)
- cost range (min/max EUR); a delivery without a real price is left out while a cost bound is set
- status, several at once (any of the ticked ones); none ticked shows every status

**F-13** — Filters are cumulative (AND); the active set is visible and clearable in one click.
**F-14** — Sort by shipping date, arrival date (the real one), cost, volume, or gross weight.
**F-15** — Company, country and city filters offer values already present in the data, so Alice picks rather than types. On the entry form these same fields autocomplete against existing values, so a repeat customer is selected rather than retyped — without this, `Example Interiors` and `example interiors` become two separate entries and the filter quietly splits her history in half.

### Status board

**F-24** — **Status.** Every delivery has one status, in this order: **To do** (no offer from a delivery company yet), **Offers received** (one or more offers in, nothing decided), **Offer accepted** (Alice took an offer, not on the way yet), **On the way**, **Delivered**. A new delivery starts at To do in the form; Alice can pick any status there, so a past delivery is still entered straight as Delivered. A delivery can move to any status, forwards, backwards or skipping: plans change.

**F-25** — **Dates by status.** A delivery has a shipping date, an expected arrival and a real arrival. All three are optional at To do, Offers received and Offer accepted. On the way requires the shipping date: once a delivery has left, that is known, but the carrier may not have given an arrival yet. Delivered requires the shipping date and the real arrival. The expected arrival is never required, and is kept after delivery so it can be compared with the real one (F-4). A save or a move that breaks this is refused with a field error naming the missing date. Only the target status's rules apply; a move never clears a date already set. The real price (`cost_eur`) and the offer price are optional at every status.

**F-26** — **Offer price.** A delivery records one offer price in EUR: the offer Alice accepted. It is what she compares the real price with (F-4) and what Estimate falls back to (F-22). At Offer accepted, On the way and Delivered, a missing offer price shows a hint on the board card and in the form; it never blocks a save or a move.

**F-27** — **The board** is the start page: one column per status, in the order of F-24. It shows every delivery that is not Delivered, and Delivered ones for one week: on the day they were moved to Delivered and the 6 days after (moved 2026-11-02: shown through 2026-11-08, gone from 2026-11-09). That day is a calendar date from the app's clock. It is set whenever a delivery becomes Delivered from another status, on the board or in the form; cleared when it leaves Delivered; set again when it comes back. Saving a delivery that stays Delivered keeps it. A delivery created at Delivered gets no day: entering a past delivery is archiving, not tracking. A Delivered delivery without that day — every record from before the board — never shows. Within a column, cards are ordered by last update, newest first; any save counts, and there is no manual order.

**F-28** — **A card** shows the customer company as its title, linking to the detail view; origin city and country → destination city and country; transport method; the shipping date when set; at To do, Offers received and Offer accepted `Expected <date>` when the expected arrival is set; at On the way `Expected <date>`, or `Expected: not set` without one, and the overdue mark of F-30; at Delivered `Arrived <date>` with the real arrival, followed by the difference of F-4 when both arrivals are set; the offer price when set, else the F-26 hint; and a **Move to** menu. Real price, cargo, notes and PDFs stay on the detail view. A **New delivery** button on the board opens the form at To do.

**F-29** — **Moving a card**, by dragging it onto a column or through its Move to menu; both behave the same. A move to On the way opens a dialog only when the shipping date is missing; it asks for the shipping date and offers the expected arrival, optional, filled in when set. With a shipping date, the move happens at once, with or without an expected arrival. A move to Delivered always opens it, asking for the real arrival, filled in with the real arrival when set, else with the expected one, so Alice confirms or corrects it to the real arrival day, plus the shipping date if missing. The expected arrival is left as it is. Confirm saves the dates and moves the card; cancel leaves the card and the delivery unchanged. Any other move happens at once.

**F-30** — **Overdue warning.** A card at On the way with an expected arrival is overdue when today, a calendar date from the app's clock, is after the expected arrival; days late = today − expected arrival. By days late its colour is yellow for 1 to 3, orange for 4 to 7 and red for 8 or more, covering the card so it stands out on the board, in the light and the dark theme. It always also shows `<n> days late` (`1 day late` for one) as text, so the level is not told by colour alone. A card on or before its expected arrival, without one, or at any other status is not marked. The mark is on the board only.

### Price estimate prompt

**F-19** — An **Estimate** screen takes a potential delivery: transport method, origin country and destination country (required); origin city, destination city, planned shipping date, pallets, m³ and gross kg (optional). A packing-list Excel can fill the three totals through the parse-without-saving path (F-8), still editable. A total of 0 or left empty counts as not given; a negative or non-numeric value is a field error and nothing is looked up until it is fixed. Nothing on the screen is saved.

**F-20** — **Chargeable quantity** in kg is the larger of gross kg and m³ × a ratio per transport method: Air 167, Sea 1000, Road 333, Rail 333 kg/m³ (rail as road until Alice confirms her partners' ratio). With only one of the two known, that one is used. With neither, or a value of 0, the delivery has no size. It applies to the potential delivery and to every past one, from its saved totals.

**F-21** — **Choosing comparables.** Candidates share the transport method, origin country and destination country, have any status except To do, and have a real price or an offer price; one with neither tells nothing about cost and is left out. When the potential delivery has a size, candidates without one are left out. Score = |ln(candidate chargeable / new chargeable)| + 0.5 × age in years (shipping date to today, days / 365.25; a shipping date after today or no shipping date counts as age 0, since such a delivery is priced from a current offer); without a size on the potential delivery, the age term alone. Lowest score first; ties go to the newer shipping date (none counts as today), then the higher id. The top 10 are kept. With fewer than 3, the destination country is ignored; still fewer than 3, the origin country too (method only). The screen and the prompt say which was ignored, and on method only warn: *"Fewer than 3 deliveries on this route; other routes with the same method are included."* No candidate at all: *"Not enough comparable deliveries"*, and no prompt.

**F-22** — **The prompt** is English, from a fixed template: a role line (freight cost analyst helping a broker), today's date, the potential delivery with its chargeable kg or *"cargo size unknown"*, the fallback warning if any, and a table of comparables (ref no. = record id, route, method, shipping and arrival dates (the real arrival; without one the expected arrival, marked `(expected)`), age in months, m³, gross kg, chargeable kg, cost EUR, notes). The cost is the real price as buy cost; without one it is the offer price, marked in the row as an offer, not a final cost, with a line under the table saying so. The screen's comparables list marks such a price "(offer)". It asks for a fixed answer format: per ref what the cost seems to include and any anomalies; refs to ignore and why; a market check, in which Claude searches the web for current rates and surcharges on the route and method, lists each source with its link and date, and compares today's market with the comparables' dates; expected buy cost EUR as low / likely / high, based on the comparables adjusted by the market check; refs that weighed most; warnings. It asks Claude to keep apart what comes from the data given and what comes from the web. Numbers are plain: no thousands separators, at most 2 decimals, trailing zeros dropped (`1580`, `4100`, `7.42`). A missing value is *"not given"* in the potential-delivery block and `-` in the table, with a line under the table saying so. Inside the table a line break in a note becomes ` / ` and `|` becomes `/`. The customer company is never in it: every company name in the archive is replaced by `[customer]` in the notes, as whole words, ignoring case, longest name first so an overlapping shorter name is not counted again; the screen shows how many were replaced.

**F-23** — The prompt sits in an editable text box that follows the form live, next to the list of comparables, each linking to its record. One button copies the box's current text, enabled once the required fields are set and at least one comparable exists. Alice pastes it into Claude herself; her paste is the consent, and nothing leaves the laptop through the app.

---

## 7. Data model

### `Delivery`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | id | auto | |
| `customer_company` | string | yes | The European company requesting the delivery. Typed manually, autocompleted against existing values (F-15). Required, because a blank makes the filter useless |
| `origin_country` | string | yes | From a country list — normalized, so filtering is exact |
| `origin_city` | string | yes | |
| `origin_postal_code` | string | no | |
| `origin_street` | string | no | |
| `destination_country` | string | yes | |
| `destination_city` | string | yes | |
| `destination_postal_code` | string | no | |
| `destination_street` | string | no | |
| `transport_method` | enum | yes | Sea, Air, Road, Rail. Exactly these four — no combinations, no "Other" |
| `status` | enum | yes | To do, Offers received, Offer accepted, On the way, Delivered (F-24). Records from before S-9 are Delivered |
| `shipping_date` | date | by status | Required at On the way and Delivered, optional before (F-25) |
| `expected_arrival_date` | date | no | The arrival the carrier gave (F-25); kept after delivery. On records from before S-10 it is the old `arrival_date` when not Delivered, else blank |
| `arrival_date` | date | by status | The real arrival; required at Delivered, optional before (F-25). On records from before S-10 it is kept when Delivered and moved to `expected_arrival_date` otherwise. A record from before S-9 without one keeps it blank until Alice next saves it |
| `offer_price_eur` | decimal(10,2) | no | The accepted offer from the delivery company (F-26). Blank on records from before S-9 |
| `cost_eur` | decimal(10,2) | no | The real price: what Alice paid for the delivery (buy cost), not what she charged her customer (OQ-11). Blank until she has it |
| `moved_to_delivered_on` | date | auto | The day the delivery last became Delivered; blank at any other status and on records from before S-9 (F-27) |
| `volume_m3` | decimal | no | From Excel, overridable (F-8) |
| `gross_weight_kg` | decimal | no | From Excel (Total G.W.), overridable |
| `pallet_count` | decimal | no | From Excel, overridable. Decimal, not integer — the column is a plain sum and nothing guarantees whole numbers |
| `notes` | text | no | Free text about this delivery (F-17). Multi-line, unlimited, unstructured — deliberately not parsed or searched |
| `excel_path` | string | no | Original upload, retained for download (F-6). Stays on `Delivery`: exactly one Excel per record, unlike the PDFs |
| `created_at` / `updated_at` | timestamp | auto | |

### `DeliveryPdf`

One row per uploaded PDF. This replaces the single `pdf_path` column on `Delivery` (F-5).

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | id | auto | |
| `delivery_id` | id | yes | Cascade delete with parent — and the stored files must be deleted alongside the rows, or the storage directory fills with orphans |
| `original_filename` | string | yes | As uploaded. This is the label in the detail view — it's how Alice tells the delivery note from the customs form, and why no separate per-file title field is needed |
| `stored_path` | string | yes | Path on disk. **Never the original filename** — two uploads can share one, and §12 shows these filenames carry U+00A0 and other surprises. Store under a generated name and keep the original for display only |
| `size_bytes` | integer | no | Shown next to the download link so a 40 MB scan isn't a surprise click |
| `sort_order` | integer | yes | Upload order, so the list is stable and Alice can reorder if she wants the delivery note first |
| `uploaded_at` | timestamp | auto | Files added later than the record itself are normal (F-5) |

> **Why a table rather than a delimited string in one column.** Three PDFs in one `pdf_path` column would need splitting on every read, break on any path containing the delimiter, and make deleting one file a string-edit. A second child table costs one migration and makes removal a `DELETE` by id.

### `DeliveryItem`

One row per item row in the uploaded Excel.

| Field | Source column | Notes |
|---|---|---|
| `id` | — | |
| `delivery_id` | — | Cascade delete with parent |
| `source_row` | — | Row number in the original sheet |
| `description` | A | Carried forward on continuation rows (§8.4) |
| `article_no` | B | See note |
| `length_mm` / `width_mm` / `height_mm` | L / M / N | Millimetres |
| `pallet_count` | F | |
| `volume_m3` | O | |
| `gross_weight_kg` | K | Blank on most rows by design (§8.5) |
| `net_weight_kg` | I | See note |
| `volume_mismatch` | — | Set by F-16 |

> **`article_no`** — the brief says the article-number column is irrelevant. I kept it because F-9 puts these rows in front of Alice, and a table of bare numbers with no row label is hard to read. Stored and displayed, never computed on.

> **`net_weight_kg`** — you chose gross weight as *the* weight, and that holds at the delivery level. Net is kept per line item only because it's the one figure most rows actually carry (§8.5); without it the item table is mostly empty. Never summed into the delivery record. Say the word and I'll drop it.

---

## 8. Excel parsing

Everything below is verified against the real file, not assumed.

### 8.1 Selecting the sheet

The sample arrived with one sheet, named `PACKING LIST ` — **with a trailing space**. That is not how the factory sends it: the original carries an invoice sheet first, which Alice currently deletes by hand before filing the file away.

The parser must therefore handle the untouched multi-sheet workbook, so that manual step disappears:

1. Prefer the sheet whose name, lowercased and whitespace-collapsed, contains `packing list`. The trailing space means exact matching fails.
2. If no name matches, scan sheets for the header signature in §8.3 and take the first that has it. An invoice sheet won't — it carries prices, not carton numbers.
3. If neither finds a sheet, stop and say so rather than falling back to the first sheet, which would be the invoice.

**Never take "the first sheet" as a default.** On an untouched file that is the invoice, and it will not parse as a packing list.

Files Alice has already trimmed still work unchanged — a single packing list sheet matches rule 1.

### 8.2 The header is three rows, not one

| Row | Contents |
|---|---|
| 1 | Main labels: `Description`, `Article Code`, `Customer Code`, `QTY/Package`, `No.of Packages`, `Total QTY`, `N.W./package`, `Total N.W.`, `G.W./package`, `Total G.W.`, `Measurement/Package`, `Total Volume`, `Carton No.`, `Container no.`, `Seal no.` |
| 2 | `L`, `W`, `H` — sub-labels under the merged `Measurement/Package` |
| 3 | Units: `Package`, `Pallet`, `PCS`, `KGS`, `KGS`, `KGS`, `KGS`, `mm`, `mm`, `mm`, `CBM`, `Fm`, `To` |

Most row-1 labels are merged vertically across rows 1–2, so their value lives only in the row-1 cell.

**The pallet column cannot be found from row 1.** Columns E and F are merged under the single label `No.of Packages`; only row 3 distinguishes `Package` (E) from `Pallet` (F). A parser matching on the top label alone takes the wrong column.

The article column's label differs between suppliers. Find it by a label containing `article`; if there is none, take the column right after `Description`. Never match a supplier-specific label.

Column map for this layout: **F = pallet count · K = Total G.W. · O = Total Volume · L/M/N = L/W/H in mm · I = Total N.W.**

### 8.3 Locating the header

Don't hard-code row numbers. Scan the first ~12 rows for a row containing both `total volume` and `carton no`; that's the label row. Then look 1–3 rows below it for a row containing both `cbm` and `pallet`; that's the unit row. Data begins on the next row. Match normalized (collapse whitespace, lowercase).

If a required column can't be mapped, stop and name the missing one rather than guessing by position.

### 8.4 Which rows are data

Read downward until a row whose first non-empty cell in A–C begins with a total marker (`total`, `sum`, `summe`, `gesamt`, `合计`, `总计`), exclusive. **In the fixture this is row 27, and it contains live `SUM()` formulas — including it doubles every figure.**

Skip any row with no numeric value anywhere in the measurement block (columns **D through O**). This removes section headers that sit inside the data — rows 4 (`QX20260115` / `Example Store Fitters`), 20 (`Hardgoods`) and 25 (`Example Store Fitters`) in the fixture.

> **Detection must scan D–O, not just the summed columns.** Only 5 of 20 item rows in the fixture carry a pallet, gross-weight or volume figure. Keying detection off the summed columns alone drops the other 15 rows — the sums stay correct, but the item table silently loses three quarters of its contents.

Rows with a blank description are continuations of the item above (the source uses vertical merges, e.g. `A5:A6`). Carry the last non-empty description forward for display.

### 8.5 Reading values

- Coerce tolerantly: strip stray unit suffixes (`12 kg`), accept comma decimals (`1,5` → `1.5`) and thousands separators.
- Blank is 0 for summing, not an error. Most rows legitimately carry only a net weight — volume, pallet count and gross weight are recorded once per pallet group and vertically merged across the rows it covers. Only the top-left cell of a merge holds a value, which is exactly what makes a plain sum correct.
- Sum **F** (pallets), **K** (gross weight), **O** (volume) across kept rows.

### 8.6 Metadata present but not captured

Cell A4 holds a shipment reference (`QX20260115` in the fixture, matching the filename) and B4 a label (`Example Store Fitters`, repeated at B25). Both are skipped as section headers per §8.4.

**B4 is not the customer name** — confirmed, despite looking like one. The European company is typed by hand (§7). Do not wire B4 into that field.

### 8.7 Volume cross-check (F-16)

`L × W × H` (mm → m) `× pallet count` should equal column O. Flag rows deviating by more than **0.01 m³**.

Threshold reasoning: O is stored rounded to 2 decimals, so exact agreement is impossible. Real files carry rounding artifacts up to about 0.005 m³; the fixture has one such row (row 26: 0.805 stored as 0.81). A 0.005 threshold sits 1% from false-firing. Rounding error doesn't grow with magnitude, so a flat 0.01 stays safe at every size — no percentage rule needed.

---

## 9. Screens

0. **Board** *(landing, `/`)* — one column per status with the cards of F-27 and F-28 and the overdue mark of F-30, moved by drag and drop or the Move to menu, the date dialog of F-29, and a New delivery button.
1. **Deliveries** *(`/deliveries`)* — filter bar (F-12) above a sortable table (F-11, F-14). Row click → detail.
2. **New / Edit delivery** — one form: company, route, method, status, shipping date, expected arrival, arrival (real), offer price, real price (F-24 to F-26), and a multi-line notes box (F-17); Excel upload with extracted totals appearing in editable fields beneath it, plus any volume-mismatch count; PDF upload accepting **multiple files at once**, showing each queued or already-attached file by filename with a remove control beside it.
3. **Delivery detail** — meta fields, status, expected and real arrival with their difference, offer price and real price with their difference (F-4), notes shown in full when present (F-18), cargo totals (with the edited marker from F-10), item table with mismatched rows highlighted (F-16), a list of attached PDFs with download links, the selected one rendered in the viewer, and a download link for the original Excel (F-5, F-6).

   With several PDFs, the viewer shows one at a time — the file list is the selector, first file selected by default. Stacking three `<iframe>` viewers down one page makes the record unreadable.
4. **Estimate** — the potential-delivery form with optional Excel upload (F-19), the chosen comparables with the fallback note (F-21), and the editable prompt with its copy button and replaced-names count (F-22, F-23).

---

## 10. Technical

### 10.1 Stack

| Layer | Choice |
|---|---|
| Frontend | Vue 3 + TypeScript, built to static assets served by the backend |
| Backend | Java, Spring Boot |
| Database | **SQLite** (see 10.3) |
| Excel | Apache POI |
| Persistence | Spring Data JDBC or jOOQ — see note |
| Migrations | Flyway |
| PDF display | `<iframe>` — browsers render PDFs natively, no library |
| File storage | On disk, directory from config. One generated filename per stored file; original names live in the database, never on disk (§7) |

**Apache POI reads both formats in one library** — XSSF for `.xlsx`, HSSF for `.xls`. `WorkbookFactory.create()` sniffs the container itself (OOXML vs OLE2) and returns the right implementation, so a file with a misleading extension — a `.xlsx` renamed to `.xls`, which happens — is handled without special casing. Both `poi` and `poi-ooxml` must be on the classpath for that detection to work.

**Persistence note:** Hibernate's SQLite dialect is community-maintained rather than first-party. Spring Data JDBC or jOOQ sit more comfortably on SQLite and, for two tables, cost nothing in expressiveness.

### 10.2 Why Java

Apache POI is the strongest Excel story of the three languages considered. Python needs two libraries (openpyxl + xlrd) and xlrd has been dormant since 2020; JavaScript's only real option for `.xls` is SheetJS, which no longer publishes current versions to npm. POI is one mature, actively maintained dependency covering both formats.

### 10.3 Why SQLite, and what it buys

One user, two tables, no Postgres feature in use — the filters are exact matches and numeric/date ranges, nothing more. Backup is copying one file.

The decisive property is **option preservation**: SQLite runs in all three deployment shapes (bundled desktop app, container, hosted server), while Postgres rules out the bundled desktop build. Choosing SQLite now keeps every packaging path open.

The trigger to revisit is **concurrency, not size**. SQLite allows one writer at a time. That's irrelevant for Alice alone and becomes the reason to move if colleagues ever write simultaneously.

### 10.4 Packaging — deliberately deferred

All three targets consume the same Spring Boot jar, so packaging is a thin final layer rather than an architectural commitment:

| Option | Cost |
|---|---|
| **Windows `.exe`** via jpackage | **Implemented** — `.github/workflows/windows-exe.yml`. jpackage does not cross-compile, so it runs on `windows-latest` with WiX. Ships a **per-user installer needing no admin rights** *and* a portable zip needing no installation, which is what makes the discovery below cheap to answer either way. Data lives in `%LOCALAPPDATA%\DewDeliveryArchive` via the `windows` profile, not beside the executable, and the app opens her browser on startup — a server with no window is otherwise indistinguishable from one that failed to start. |
| **Docker Compose** on her machine | Needs Docker Desktop: WSL2, BIOS virtualisation, **admin rights to install**, ~1–2 GB idle RAM. Wrap `docker compose up -d` in a `start.bat` for a double-click. Free below 250 employees / $10M revenue; Podman or Rancher Desktop otherwise. |
| **Hosted on a Linux server** | Dissolves the Linux-dev/Windows-user mismatch entirely — Alice opens a URL, nothing installed. Needs a box and a shared password or IP restriction. Cheapest path to adding colleagues later. |

Two disciplines keep all three open, both good practice regardless:

1. **Externalise config** — database URL, file storage directory, port. Spring profiles and env vars. Use `java.nio.Path`, never string-concatenated separators, so Windows paths don't bite.
2. **Keep SQL portable** — no `JSONB`, arrays, `ILIKE` or `RETURNING`. Free here, since the app wants none of them.

> **The real deadline is data, not code.** Switching database or packaging is cheap until Alice has entered records she can't afford to lose; after that it's an export/import migration carrying real risk. Note also that SQLite's `ALTER TABLE` is limited — some Flyway migrations will need the create-copy-drop-rename pattern rather than a straight alter.

**Not deferred: the discovery.** Whether Alice can install software on her laptop should be established early. If IT has it locked down, the Docker option disappears at zero cost, and that's worth knowing before building around it.

---

## 11. Verified parser behaviour

The parser implementing §8 reproduces the fixture's own `TOTAL` row exactly, without ever reading it:

| Figure | Parsed | Sheet's TOTAL row (row 27) |
|---|---|---|
| Pallets | 6 | 6 |
| Volume | 7.42 m³ | 7.42 |
| Gross weight | 1580 kg | 1580 |
| Net weight | 1264.8 kg | 1264.8 |

20 item rows kept; 3 section rows skipped (4, 20, 25); TOTAL row excluded. All 5 rows carrying dimensions pass the volume cross-check at a 0.01 m³ threshold.

## 12. Acceptance test

Fixtures, **both in this repo**, hold invented data in the §8 layout, and both → **20 items · 6 pallets · 7.42 m³ · 1580 kg gross** (net 1264.8 kg summed over items):

| Fixture | Exercises |
|---|---|
| `samples/QX20260115-packing-list.xlsx` | the OOXML path (POI XSSF) |
| `samples/QX20260115-packing-list.xls` | the **legacy OLE2 path** (POI HSSF) — the same sheet saved as Excel 97 |

The fixture keeps every trait §8 relies on: one sheet named `PACKING LIST ` with a trailing space; the three header rows with the merged `No.of Packages` and `Measurement/Package` labels; section rows 4, 20 and 25; continuation rows with a blank, vertically merged description; pallet, gross-weight and volume figures vertically merged across their pallet group; one row with the rounding artifact of §8.7; and a `TOTAL` row with live `SUM()` formulas. Shipment reference, company names, descriptions and codes are invented.

A third case is asserted without a stored file: the `.xlsx` **renamed** to `.xls`, which §10.1 says "happens". All three must agree, because `WorkbookFactory.create()` sniffs the container rather than trusting the extension. Any parser change must keep reproducing these.

These are the values the sheet's own `TOTAL` row carries, so the test is checking the parser against the sheet's own arithmetic. The parser must not read that row to get them — it's excluded per §8.4.

Real packing lists arrive with names like `<reference> INV & PAC.xlsx` whose spaces are **U+00A0 non-breaking spaces**, not ASCII spaces. Shell globs and exact-path lookups fail on them in confusing ways. Expect the same in files Alice uploads, and don't derive anything from filenames.

---

## 13. Resolved questions

All closed. Recorded so the reasoning isn't re-litigated later.

| | Question | Resolution |
|---|---|---|
| OQ-1 | Does the layout vary between files? | **Identical across all files.** The column map in §8.2 can be relied on. Detection logic in §8.3 is kept anyway — it costs nothing and fails loudly instead of silently if a variant ever appears. |
| OQ-2 | Is there a second invoice sheet? | **Yes** — Alice deletes it by hand today. The parser selects the packing list by name instead (§8.1), so she can stop. |
| OQ-3 | Transport methods? | **Sea, Air, Road, Rail.** No combinations, no "Other". |
| OQ-4 | Keep the original Excel? | **Keep and allow download** (F-6). One column on `Delivery`; unlike the PDFs it stays single (superseded in part by OQ-8). |
| OQ-5 | Filter by European company? | **Added** — `customer_company` (§7), filterable (F-12), autocompleted to prevent near-duplicate names fragmenting the filter (F-15). |
| OQ-6 | Auto-fill company from the Excel? | **No.** `B4` looks like a customer name but isn't one (§8.6). Manual entry. |
| OQ-7 | Parse the invoice sheet for company/address? | **No.** Meta fields stay manual by choice. |
| OQ-8 | One PDF per delivery, or several? | **Several** (F-5). A delivery arrives with a delivery note plus, often, a bill of lading or customs paperwork; forcing a choice between them meant the rest went unfiled. Costs a child table (§7). |
| OQ-9 | Do the PDFs need titles or descriptions? | **No.** The original filename is the label. Alice names files meaningfully already, and a per-file title field is one more thing to fill in on every upload. Revisit only if the filenames turn out to be opaque scanner output. |
| OQ-10 | Notes per delivery — where do they surface? | Free text on the delivery (F-17), shown in full on the detail view, marked but not shown in the list (F-18). Not searchable in v1 (§3). |
| OQ-11 | Is `cost_eur` what Alice paid or what she charged? | **What she paid** (buy cost). An estimate built on it is her expected buy cost; she adds her margin herself (F-22). |
| OQ-12 | One offer price per delivery, or every offer received? | **One**, the accepted offer (F-26). Recording every offer with its company and date, and the carrier, is a later story. |

## 14. Decision log — stack

| | Decision | Reasoning |
|---|---|---|
| SD-1 | Vue 3 + TypeScript frontend | Proposed by the developer, who works in it daily. Earns its keep most on the upload screen, where totals update live and mismatched rows need highlighting. Mild overkill for the other two screens — accepted. |
| SD-2 | Java / Spring Boot backend | Apache POI is the best `.xls` + `.xlsx` story available (§10.2). This reversed an earlier proposal of Python, which was based on POI being overlooked. |
| SD-3 | SQLite, not Postgres | Nothing in the app uses a Postgres feature, and SQLite is the only choice that keeps all three packaging options open (§10.3). Revisit on concurrent writers, not on data volume. |
| SD-4 | Packaging deferred | All targets consume the same jar; the two disciplines in §10.4 keep the choice cheap until Alice has irreplaceable data. |
| SD-5 | Hand-written `NamedParameterJdbcTemplate`, **not** Spring Data JDBC repositories | Settled during the build, both reasons verified rather than assumed. Spring Data Relational 3.4.1 ships **no SQLite dialect** and its `DialectResolver` *throws* instead of falling back, so the repository auto-configuration cannot start on SQLite at all. Separately, saving a Spring Data JDBC aggregate deletes and re-inserts its children, which would hand every PDF a **new id** on any parent edit — and that id is part of the API. Still `spring-jdbc`, still §10.1's stack; just not the repository abstraction. |
| SD-6 | `foreign_keys=on` pinned on the JDBC url | SQLite disables foreign-key enforcement **per connection**, so every `ON DELETE CASCADE` in the schema would otherwise be silently inert. A migration test proves the cascade rather than trusting it. |
| SD-7 | Frontend built into the jar by `frontend-maven-plugin`, which downloads its own Node | One `mvn package` produces the whole app, and it builds identically on a dev machine and on the Windows CI runner jpackage needs (§10.4). `-Dskip.frontend=true` skips the npm work for Java-only runs. |

## 15. Status and next step

Steps 1–3 are **done and verified end to end** against the packaged jar: create a record, upload the real packing list, override a total and watch it survive the upload, attach two PDFs in one request, filter and sort, then delete and watch the rows *and* the files leave disk.

1. ~~Parser, against the acceptance test in §12.~~ **Done** — §12 reproduced on both fixtures, 22 tests.
2. ~~Schema + migrations, CRUD, list/filter view.~~ **Done** — 96 tests.
3. ~~Upload flow, editable totals, volume cross-check, multi-PDF attach and remove.~~ **Done** — three screens, one runnable jar.
4. **Packaging — Windows is built** (`.github/workflows/windows-exe.yml`, §10.4). The discovery §10.4 names — *can Alice install software on her laptop?* — is now cheap either way: the workflow emits a per-user installer that needs no admin rights **and** a portable zip that needs no installation. Ask her, then hand over whichever fits. Docker and the hosted option remain untouched and unneeded unless colleagues start writing concurrently (§10.3).

Two known gaps, neither blocking:

- **No `.xls` file from the factory has been seen.** The legacy fixture is a conversion of the real `.xlsx`, so it proves POI's HSSF path and the column map, not that a genuine factory `.xls` looks the same. OQ-1 says the layout is identical across files; this just hasn't been confirmed on a real binary-format file.
- **`PUT .../excel` deliberately leaves the delivery's three totals untouched** (F-8: saved totals are authoritative). Driven through the UI this is invisible, because the form sends the totals on save. Driven through the API alone, uploading an Excel leaves them `null` while `item_sums` carries the parsed figures. Coherent, and worth knowing before anyone scripts against the API.
