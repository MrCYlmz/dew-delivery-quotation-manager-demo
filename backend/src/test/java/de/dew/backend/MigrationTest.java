package de.dew.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class MigrationTest extends BackendTest {

    @Test
    void flywayAppliedTheBaselineAndTheStatusMigration() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(versions).hasSizeGreaterThan(1).first().isEqualTo("1");
    }

    @Test
    void allThreeTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name", String.class);
        assertThat(tables).contains("delivery", "delivery_pdf", "delivery_item");
    }

    @Test
    void transportMethodIsConstrainedToTheFourOfOq3() {
        for (String valid : List.of("SEA", "AIR", "ROAD", "RAIL")) {
            assertThat(insertDelivery(valid)).isPositive();
        }
        assertThatThrownBy(() -> insertDelivery("OTHER"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertDelivery("sea"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void deletingADeliveryCascadesToItemsAndPdfRows() {
        long id = insertDelivery("SEA");
        jdbc.update("INSERT INTO delivery_item (delivery_id, source_row, description, volume_mismatch)"
                + " VALUES (?, 5, 'Chair', 0)", id);
        jdbc.update("INSERT INTO delivery_pdf"
                + " (delivery_id, original_filename, stored_path, size_bytes, sort_order, uploaded_at)"
                + " VALUES (?, 'note.pdf', 'generated-1.pdf', 10, 0, '2026-07-05T09:12:00Z')", id);

        jdbc.update("DELETE FROM delivery WHERE id = ?", id);

        assertThat(count("delivery_item")).isZero();
        assertThat(count("delivery_pdf")).isZero();
    }

    @Test
    void aPdfRowCannotReferenceAMissingDelivery() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO delivery_pdf"
                + " (delivery_id, original_filename, stored_path, sort_order, uploaded_at)"
                + " VALUES (999999, 'x.pdf', 'gen.pdf', 0, '2026-07-05T09:12:00Z')"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void isoTextDatesSortAndRangeCompareChronologically() {
        insertDelivery("SEA", "2026-01-09");
        insertDelivery("AIR", "2026-02-01");
        insertDelivery("ROAD", "2026-11-30");

        assertThat(jdbc.queryForList(
                "SELECT shipping_date FROM delivery ORDER BY shipping_date DESC", String.class))
                .containsExactly("2026-11-30", "2026-02-01", "2026-01-09");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM delivery WHERE shipping_date >= ? AND shipping_date <= ?",
                Integer.class, "2026-01-09", "2026-02-01"))
                .isEqualTo(2);
    }

    private long insertDelivery(String method) {
        return insertDelivery(method, "2026-07-02");
    }

    private long insertDelivery(String method, String shippingDate) {
        jdbc.update("INSERT INTO delivery (customer_company, origin_country, origin_city,"
                + " destination_country, destination_city, transport_method, status, shipping_date,"
                + " cost_eur, created_at, updated_at)"
                + " VALUES ('Example Interiors', 'CN', 'Ningbo', 'DE', 'Munich', ?, 'DELIVERED', ?, 4210.00,"
                + " '2026-07-02T00:00:00Z', '2026-07-02T00:00:00Z')", method, shippingDate);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
