package de.dew.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
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
 * Alice's database already holds rows written under V2, so the S-10 migration is run against a
 * database that was migrated to V2 and filled before it, not against an empty one.
 */
class ArrivalDatesMigrationTest {

    // Every column V2 has on delivery except arrival_date, which the migration may move.
    private static final String V2_COLUMNS_BUT_ARRIVAL = """
            id, customer_company, origin_country, origin_city, origin_postal_code, origin_street,
            destination_country, destination_city, destination_postal_code, destination_street,
            transport_method, status, shipping_date, offer_price_eur, cost_eur, volume_m3,
            gross_weight_kg, pallet_count, notes, excel_path, excel_original_filename,
            moved_to_delivered_on, created_at, updated_at
            """;

    @TempDir
    Path dir;

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    // Status -> id of the row seeded at that status with an arrival date.
    private final Map<String, Long> withArrival = new LinkedHashMap<>();
    private long deliveredWithoutArrival;
    private long toDoWithoutArrival;

    @BeforeEach
    void fillAVersionTwoDatabase() {
        // foreign_keys=on as in application.yml, so a copy-and-rename migration must survive
        // ON DELETE CASCADE.
        dataSource = new DriverManagerDataSource(
                "jdbc:sqlite:" + dir.resolve("deliveries.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        flyway().target("2").load().migrate();

        for (String status : List.of("TO_DO", "OFFERS_RECEIVED", "OFFER_ACCEPTED", "ON_THE_WAY",
                "DELIVERED")) {
            withArrival.put(status, insert(status, "2026-08-21"));
        }
        jdbc.update("UPDATE delivery SET origin_postal_code = '315000', origin_street = 'Harbour"
                + " Road 1', destination_postal_code = '80331', destination_street = 'Marienplatz"
                + " 1', offer_price_eur = 3900.00, volume_m3 = 7.42, gross_weight_kg = 1580,"
                + " pallet_count = 6, notes = 'rush premium', excel_path = 'gen-excel-1.xlsx',"
                + " excel_original_filename = 'QX20260115.xlsx',"
                + " moved_to_delivered_on = '2026-08-21' WHERE id = ?",
                withArrival.get("DELIVERED"));
        deliveredWithoutArrival = insert("DELIVERED", null);
        toDoWithoutArrival = insert("TO_DO", null);

        long delivered = withArrival.get("DELIVERED");
        jdbc.update("INSERT INTO delivery_item (delivery_id, source_row, description, volume_m3,"
                + " volume_mismatch) VALUES (?, 5, 'Chair', 2.02, 1)", delivered);
        jdbc.update("INSERT INTO delivery_pdf (delivery_id, original_filename, stored_path,"
                + " size_bytes, sort_order, uploaded_at)"
                + " VALUES (?, 'note.pdf', 'gen-pdf-1.pdf', 10, 0, '2026-07-05T09:12:00Z')", delivered);
    }

    @Test
    void aDeliveredRecordKeepsItsArrivalAsTheRealOneWithABlankExpectedOne_S10Criterion3() {
        flyway().load().migrate();

        Map<String, Object> row = arrivals(withArrival.get("DELIVERED"));
        assertThat(row.get("arrival_date")).isEqualTo("2026-08-21");
        assertThat(row.get("expected_arrival_date")).isNull();
    }

    @Test
    void everyOtherRecordGetsItsArrivalAsTheExpectedOneWithABlankRealOne_S10Criterion3() {
        flyway().load().migrate();

        for (String status : List.of("TO_DO", "OFFERS_RECEIVED", "OFFER_ACCEPTED", "ON_THE_WAY")) {
            Map<String, Object> row = arrivals(withArrival.get(status));
            assertThat(row.get("expected_arrival_date")).as(status).isEqualTo("2026-08-21");
            assertThat(row.get("arrival_date")).as(status).isNull();
        }
    }

    @Test
    void aRecordWithoutAnArrivalDateGetsNeither_S10Criterion3() {
        flyway().load().migrate();

        for (long id : List.of(deliveredWithoutArrival, toDoWithoutArrival)) {
            Map<String, Object> row = arrivals(id);
            assertThat(row.get("expected_arrival_date")).isNull();
            assertThat(row.get("arrival_date")).isNull();
        }
    }

    @Test
    void everyOtherValueOfAnExistingRecordIsUnchanged_S10Criterion3() {
        List<Map<String, Object>> before = otherColumns();

        flyway().load().migrate();

        assertThat(otherColumns()).isEqualTo(before);
    }

    @Test
    void itemsAndPdfsOfExistingRecordsSurvive_S10Criterion3() {
        flyway().load().migrate();

        long delivered = withArrival.get("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_item WHERE delivery_id = ?",
                Integer.class, delivered)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_pdf WHERE delivery_id = ?",
                Integer.class, delivered)).isEqualTo(1);
    }

    @Test
    void deletingAMigratedRecordStillCascadesToItemsAndPdfs_S10Criterion3() {
        flyway().load().migrate();

        jdbc.update("DELETE FROM delivery WHERE id = ?", withArrival.get("DELIVERED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_item", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_pdf", Integer.class)).isZero();
    }

    @Test
    void aNewRecordNeverReusesTheIdOfARecordDeletedBeforeTheMigration_S10Criterion3() {
        // Deleted under V2, so only the AUTOINCREMENT counter still remembers this highest id.
        jdbc.update("DELETE FROM delivery WHERE id = ?", toDoWithoutArrival);
        flyway().load().migrate();

        insert("TO_DO", null);

        assertThat(lastId()).isGreaterThan(toDoWithoutArrival);
    }

    private long insert(String status, String arrivalDate) {
        jdbc.update("""
                INSERT INTO delivery (customer_company, origin_country, origin_city,
                    destination_country, destination_city, transport_method, status,
                    shipping_date, arrival_date, cost_eur, created_at, updated_at)
                VALUES ('Example Interiors', 'CN', 'Ningbo', 'DE', 'Munich', 'SEA', ?, '2026-07-02',
                    ?, 4210.00, '2026-07-02T08:00:00Z', '2026-07-05T09:30:00Z')
                """, status, arrivalDate);
        return lastId();
    }

    private FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }

    private Map<String, Object> arrivals(long id) {
        return jdbc.queryForMap(
                "SELECT expected_arrival_date, arrival_date FROM delivery WHERE id = ?", id);
    }

    private List<Map<String, Object>> otherColumns() {
        return jdbc.queryForList("SELECT " + V2_COLUMNS_BUT_ARRIVAL + " FROM delivery ORDER BY id");
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM delivery", Long.class);
    }
}
