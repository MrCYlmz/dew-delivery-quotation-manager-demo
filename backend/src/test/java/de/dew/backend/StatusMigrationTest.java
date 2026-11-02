package de.dew.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Alice's database already holds V1 rows, so the S-9 migration is run against a database that
 * was migrated to V1 and filled before it, not against an empty one.
 */
class StatusMigrationTest {

    // Every column V1 created on delivery, so "all other values unchanged" covers each of them.
    private static final String V1_COLUMNS = """
            id, customer_company, origin_country, origin_city, origin_postal_code, origin_street,
            destination_country, destination_city, destination_postal_code, destination_street,
            transport_method, shipping_date, arrival_date, cost_eur, volume_m3, gross_weight_kg,
            pallet_count, notes, excel_path, excel_original_filename, created_at, updated_at
            """;

    @TempDir
    Path dir;

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private long complete;
    private long withoutArrival;

    @BeforeEach
    void fillAVersionOneDatabase() {
        // foreign_keys=on as in application.yml: dropping a parent table with it on deletes the
        // children through ON DELETE CASCADE, which a copy-and-rename migration must survive.
        dataSource = new DriverManagerDataSource(
                "jdbc:sqlite:" + dir.resolve("deliveries.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        flyway().target("1").load().migrate();

        jdbc.update("""
                INSERT INTO delivery (customer_company, origin_country, origin_city,
                    origin_postal_code, origin_street, destination_country, destination_city,
                    destination_postal_code, destination_street, transport_method, shipping_date,
                    arrival_date, cost_eur, volume_m3, gross_weight_kg, pallet_count, notes,
                    excel_path, excel_original_filename, created_at, updated_at)
                VALUES ('Example Interiors', 'CN', 'Ningbo', '315000', 'Harbour Road 1', 'DE',
                    'Munich', '80331', 'Marienplatz 1', 'SEA', '2026-07-02', '2026-08-21', 4210.00,
                    7.42, 1580, 6, 'rush premium', 'gen-excel-1.xlsx', 'QX20260115.xlsx',
                    '2026-07-02T08:00:00Z', '2026-07-05T09:30:00Z')
                """);
        complete = lastId();
        jdbc.update("""
                INSERT INTO delivery (customer_company, origin_country, origin_city,
                    destination_country, destination_city, transport_method, shipping_date,
                    cost_eur, created_at, updated_at)
                VALUES ('Nordwind GmbH', 'VN', 'Haiphong', 'PL', 'Gdansk', 'RAIL', '2025-11-30',
                    2600.50, '2025-12-01T10:00:00Z', '2025-12-01T10:00:00Z')
                """);
        withoutArrival = lastId();

        jdbc.update("INSERT INTO delivery_item (delivery_id, source_row, description, volume_m3,"
                + " volume_mismatch) VALUES (?, 5, 'Chair', 2.02, 1)", complete);
        jdbc.update("INSERT INTO delivery_pdf (delivery_id, original_filename, stored_path,"
                + " size_bytes, sort_order, uploaded_at)"
                + " VALUES (?, 'note.pdf', 'gen-pdf-1.pdf', 10, 0, '2026-07-05T09:12:00Z')", complete);
    }

    @Test
    void everyExistingRecordBecomesDeliveredWithoutOfferPriceOrDay_S9Migration() {
        List<Map<String, Object>> before = v1Rows();

        flyway().load().migrate();

        List<Map<String, Object>> migrated = jdbc.queryForList(
                "SELECT status, offer_price_eur, moved_to_delivered_on FROM delivery ORDER BY id");
        assertThat(migrated).hasSize(before.size());
        for (Map<String, Object> row : migrated) {
            assertThat(row.get("status")).isEqualTo("DELIVERED");
            assertThat(row.get("offer_price_eur")).isNull();
            assertThat(row.get("moved_to_delivered_on")).isNull();
        }
    }

    @Test
    void everyOtherValueOfAnExistingRecordIsUnchanged_S9Migration() {
        List<Map<String, Object>> before = v1Rows();

        flyway().load().migrate();

        assertThat(v1Rows()).isEqualTo(before);
    }

    @Test
    void aRecordWithoutAnArrivalDateKeepsItBlank_S9Migration() {
        flyway().load().migrate();

        assertThat(jdbc.queryForObject("SELECT arrival_date FROM delivery WHERE id = ?",
                String.class, withoutArrival)).isNull();
    }

    @Test
    void itemsAndPdfsOfExistingRecordsSurvive_S9Migration() {
        flyway().load().migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_item WHERE delivery_id = ?",
                Integer.class, complete)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_pdf WHERE delivery_id = ?",
                Integer.class, complete)).isEqualTo(1);
    }

    @Test
    void deletingAMigratedRecordStillCascadesToItemsAndPdfs_S9Migration() {
        flyway().load().migrate();

        jdbc.update("DELETE FROM delivery WHERE id = ?", complete);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_item", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_pdf", Integer.class)).isZero();
    }

    @Test
    void aNewRecordNeverReusesTheIdOfARecordDeletedBeforeTheMigration_S9Migration() {
        // Deleted under V1, so only the AUTOINCREMENT counter still remembers this highest id.
        jdbc.update("DELETE FROM delivery WHERE id = ?", withoutArrival);
        flyway().load().migrate();

        jdbc.update("""
                INSERT INTO delivery (customer_company, origin_country, origin_city,
                    destination_country, destination_city, transport_method, status,
                    created_at, updated_at)
                VALUES ('Kestrel Imports', 'CN', 'Ningbo', 'DE', 'Munich', 'AIR', 'TO_DO',
                    '2026-10-30T08:00:00Z', '2026-10-30T08:00:00Z')
                """);

        assertThat(lastId()).isGreaterThan(withoutArrival);
    }

    private FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }

    private List<Map<String, Object>> v1Rows() {
        return jdbc.queryForList("SELECT " + V1_COLUMNS + " FROM delivery ORDER BY id");
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM delivery", Long.class);
    }
}
