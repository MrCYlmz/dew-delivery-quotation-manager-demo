-- S-9: status, offer price and the "moved to Delivered" day (SPEC.md F-24 to F-27), and
-- shipping_date and cost_eur become optional.
--
-- SQLite's ALTER TABLE cannot drop a NOT NULL, so the table is rebuilt: create, copy, drop,
-- rename (https://www.sqlite.org/lang_altertable.html#otheralter). Every row from before
-- S-9 is a past delivery, so the copy backfills status DELIVERED and leaves the new columns
-- blank; no other value is touched.
--
-- foreign_keys must be off for the drop: with it on, dropping delivery deletes its rows and
-- ON DELETE CASCADE takes every item and PDF row with them. The pragma is a no-op inside a
-- transaction, so the .conf next to this script runs it outside Flyway's; the savepoint
-- keeps the rebuild itself atomic.

PRAGMA foreign_keys = OFF;

SAVEPOINT status_and_offer_price;

CREATE TABLE delivery_new (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_company            TEXT    NOT NULL,

    origin_country              TEXT    NOT NULL,
    origin_city                 TEXT    NOT NULL,
    origin_postal_code          TEXT,
    origin_street               TEXT,
    destination_country         TEXT    NOT NULL,
    destination_city            TEXT    NOT NULL,
    destination_postal_code     TEXT,
    destination_street          TEXT,

    transport_method            TEXT    NOT NULL
                                CHECK (transport_method IN ('SEA', 'AIR', 'ROAD', 'RAIL')),

    -- F-24, stored by name. No default: every insert says which status it means.
    status                      TEXT    NOT NULL
                                CHECK (status IN ('TO_DO', 'OFFERS_RECEIVED', 'OFFER_ACCEPTED',
                                                  'ON_THE_WAY', 'DELIVERED')),

    -- F-25: required at ON_THE_WAY and DELIVERED only, which the service enforces; a record
    -- from before S-9 without an arrival date keeps it blank until it is next saved.
    shipping_date               TEXT,
    arrival_date                TEXT,

    -- F-26: the accepted offer. cost_eur is the real price, blank until Alice has the invoice.
    offer_price_eur             NUMERIC,
    cost_eur                    NUMERIC,

    volume_m3                   NUMERIC,
    gross_weight_kg             NUMERIC,
    pallet_count                NUMERIC,

    notes                       TEXT,

    excel_path                  TEXT,
    excel_original_filename     TEXT,

    -- F-27: ISO date the delivery last became DELIVERED, blank at any other status.
    moved_to_delivered_on       TEXT,

    created_at                  TEXT    NOT NULL,
    updated_at                  TEXT    NOT NULL
);

INSERT INTO delivery_new (id, customer_company, origin_country, origin_city, origin_postal_code,
    origin_street, destination_country, destination_city, destination_postal_code,
    destination_street, transport_method, status, shipping_date, arrival_date, offer_price_eur,
    cost_eur, volume_m3, gross_weight_kg, pallet_count, notes, excel_path,
    excel_original_filename, moved_to_delivered_on, created_at, updated_at)
SELECT id, customer_company, origin_country, origin_city, origin_postal_code,
    origin_street, destination_country, destination_city, destination_postal_code,
    destination_street, transport_method, 'DELIVERED', shipping_date, arrival_date, NULL,
    cost_eur, volume_m3, gross_weight_kg, pallet_count, notes, excel_path,
    excel_original_filename, NULL, created_at, updated_at
FROM delivery;

-- Carry the AUTOINCREMENT counter over, so the id of a record Alice deleted is never reused.
DELETE FROM sqlite_sequence WHERE name = 'delivery_new';
INSERT INTO sqlite_sequence (name, seq)
SELECT 'delivery_new', seq FROM sqlite_sequence WHERE name = 'delivery';

DROP TABLE delivery;

ALTER TABLE delivery_new RENAME TO delivery;

CREATE INDEX idx_delivery_shipping_date     ON delivery (shipping_date);
CREATE INDEX idx_delivery_arrival_date      ON delivery (arrival_date);
CREATE INDEX idx_delivery_cost_eur          ON delivery (cost_eur);
CREATE INDEX idx_delivery_customer_company  ON delivery (customer_company);
CREATE INDEX idx_delivery_origin            ON delivery (origin_country, origin_city);
CREATE INDEX idx_delivery_destination       ON delivery (destination_country, destination_city);
-- F-12's status filter and F-27's board.
CREATE INDEX idx_delivery_status            ON delivery (status, moved_to_delivered_on);

RELEASE status_and_offer_price;

PRAGMA foreign_keys = ON;
