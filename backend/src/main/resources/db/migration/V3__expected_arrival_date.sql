-- S-10: an expected arrival next to the real one (SPEC.md F-25, F-30).
--
-- The new column is optional at every status, so ADD COLUMN does and no rebuild is needed.
-- Before S-10, arrival_date was the expected arrival on a record not yet Delivered and the
-- real one on a Delivered record; the update moves the former, so no record loses a date.

ALTER TABLE delivery ADD COLUMN expected_arrival_date TEXT;

UPDATE delivery
SET expected_arrival_date = arrival_date,
    arrival_date = NULL
WHERE status <> 'DELIVERED';
