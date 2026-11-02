-- Baseline schema: SPEC.md section 7, three tables.
--
-- Everything that a later migration could not add cheaply is decided here on purpose.
-- SQLite's ALTER TABLE cannot add a CHECK constraint, add a foreign key, or change a
-- column type; doing any of those later means create-copy-drop-rename (SPEC.md 10.4).
-- So the enum check, the cascades and the column types are in V1 and not deferred.
--
-- Portability (SPEC.md 10.4, discipline 2): no JSONB, no arrays, no ILIKE, no RETURNING.
-- Dates are ISO-8601 TEXT (yyyy-MM-dd) because SQLite has no date type; ISO text sorts
-- and range-compares chronologically, which is all F-12's ranges and F-14's sorts need.
-- Timestamps are ISO-8601 instants in TEXT, UTC, seconds precision.

CREATE TABLE delivery (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,

    -- F-15: typed by hand, autocompleted against existing values. Required, because a
    -- blank customer makes the filter useless.
    customer_company            TEXT    NOT NULL,

    origin_country              TEXT    NOT NULL,
    origin_city                 TEXT    NOT NULL,
    origin_postal_code          TEXT,
    origin_street               TEXT,
    destination_country         TEXT    NOT NULL,
    destination_city            TEXT    NOT NULL,
    destination_postal_code     TEXT,
    destination_street          TEXT,

    -- OQ-3: exactly these four, no combinations and no "OTHER". Enforced in the database
    -- as well as in the Java enum, because this is the constraint SQLite cannot add later.
    transport_method            TEXT    NOT NULL
                                CHECK (transport_method IN ('SEA', 'AIR', 'ROAD', 'RAIL')),

    shipping_date               TEXT    NOT NULL,
    arrival_date                TEXT,

    cost_eur                    NUMERIC NOT NULL,

    -- From the Excel, overridable, and once overridden authoritative (F-8). Nullable:
    -- F-7 allows a record with no Excel at all.
    volume_m3                   NUMERIC,
    gross_weight_kg             NUMERIC,
    -- Decimal, not integer: the source column is a plain sum and nothing guarantees
    -- whole numbers (SPEC.md section 7).
    pallet_count                NUMERIC,

    -- F-17. Deliberately unstructured, unparsed and unindexed; not searchable in v1 (section 3).
    notes                       TEXT,

    -- F-6: exactly one Excel per record, so it stays a column rather than a child table.
    -- Two columns for one file for the same reason delivery_pdf has two: the disk name is
    -- generated and the original is kept for the download (section 7, section 12).
    excel_path                  TEXT,
    excel_original_filename     TEXT,

    created_at                  TEXT    NOT NULL,
    updated_at                  TEXT    NOT NULL
);

-- F-12 filter columns and F-14 sort columns.
CREATE INDEX idx_delivery_shipping_date     ON delivery (shipping_date);
CREATE INDEX idx_delivery_arrival_date      ON delivery (arrival_date);
CREATE INDEX idx_delivery_cost_eur          ON delivery (cost_eur);
CREATE INDEX idx_delivery_customer_company  ON delivery (customer_company);
CREATE INDEX idx_delivery_origin            ON delivery (origin_country, origin_city);
CREATE INDEX idx_delivery_destination       ON delivery (destination_country, destination_city);

-- One row per uploaded PDF (F-5). Replaces the single pdf_path column: a delimited string
-- would break on any path containing the delimiter and make removing one file a string edit.
CREATE TABLE delivery_pdf (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    delivery_id         INTEGER NOT NULL REFERENCES delivery (id) ON DELETE CASCADE,

    -- The label in the detail view: how Alice tells the delivery note from the customs
    -- form, which is why no separate per-file title field exists (OQ-9).
    original_filename   TEXT    NOT NULL,
    -- Generated, never the original filename: two uploads can share one, and section 12's
    -- real filenames carry U+00A0. Relative to the configured storage directory, so the
    -- database survives that directory moving between packaging targets.
    stored_path         TEXT    NOT NULL,
    size_bytes          INTEGER,
    -- Upload order, so the file list is stable and reorderable (section 7).
    sort_order          INTEGER NOT NULL,
    uploaded_at         TEXT    NOT NULL
);

CREATE INDEX idx_delivery_pdf_delivery ON delivery_pdf (delivery_id, sort_order);
CREATE UNIQUE INDEX idx_delivery_pdf_stored_path ON delivery_pdf (stored_path);

-- One row per item row in the uploaded Excel (F-9). All measurement columns are nullable:
-- most rows legitimately carry only a net weight, because volume, pallet count and gross
-- weight are recorded once per pallet group and vertically merged (SPEC.md 8.5). Blank
-- counts as zero for summing, not at rest.
CREATE TABLE delivery_item (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    delivery_id         INTEGER NOT NULL REFERENCES delivery (id) ON DELETE CASCADE,

    source_row          INTEGER NOT NULL,
    description         TEXT,
    article_no          TEXT,
    length_mm           NUMERIC,
    width_mm            NUMERIC,
    height_mm           NUMERIC,
    pallet_count        NUMERIC,
    volume_m3           NUMERIC,
    gross_weight_kg     NUMERIC,
    net_weight_kg       NUMERIC,
    -- F-16 / 8.7, threshold 0.01 m3. Computed by the parser, stored so the detail view can
    -- highlight the row without re-reading the workbook.
    volume_mismatch     INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_delivery_item_delivery ON delivery_item (delivery_id, source_row);
