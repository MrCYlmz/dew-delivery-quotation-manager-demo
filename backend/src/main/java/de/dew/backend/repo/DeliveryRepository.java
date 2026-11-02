package de.dew.backend.repo;

import de.dew.backend.domain.Delivery;
import de.dew.backend.domain.DeliveryQuery;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.domain.DeliverySummary;
import de.dew.backend.domain.TransportMethod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryRepository {

    private static final String COLUMNS = """
            id, customer_company, origin_country, origin_city, origin_postal_code, origin_street,
            destination_country, destination_city, destination_postal_code, destination_street,
            transport_method, status, shipping_date, expected_arrival_date, arrival_date,
            offer_price_eur, cost_eur, volume_m3, gross_weight_kg, pallet_count, notes, excel_path,
            excel_original_filename, moved_to_delivered_on, created_at, updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DeliveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Delivery insert(Delivery delivery) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO delivery (customer_company, origin_country, origin_city,
                    origin_postal_code, origin_street, destination_country, destination_city,
                    destination_postal_code, destination_street, transport_method, status,
                    shipping_date, expected_arrival_date, arrival_date, offer_price_eur, cost_eur,
                    volume_m3, gross_weight_kg, pallet_count, notes, excel_path,
                    excel_original_filename, moved_to_delivered_on, created_at, updated_at)
                VALUES (:customer_company, :origin_country, :origin_city, :origin_postal_code,
                    :origin_street, :destination_country, :destination_city,
                    :destination_postal_code, :destination_street, :transport_method, :status,
                    :shipping_date, :expected_arrival_date, :arrival_date, :offer_price_eur,
                    :cost_eur, :volume_m3, :gross_weight_kg, :pallet_count, :notes, :excel_path,
                    :excel_original_filename, :moved_to_delivered_on, :created_at, :updated_at)
                """, parameters(delivery), keys, new String[] {"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert returned no generated key for delivery");
        }
        return delivery.withId(key.longValue());
    }

    public boolean update(Delivery delivery) {
        int rows = jdbc.update("""
                UPDATE delivery SET
                    customer_company = :customer_company,
                    origin_country = :origin_country,
                    origin_city = :origin_city,
                    origin_postal_code = :origin_postal_code,
                    origin_street = :origin_street,
                    destination_country = :destination_country,
                    destination_city = :destination_city,
                    destination_postal_code = :destination_postal_code,
                    destination_street = :destination_street,
                    transport_method = :transport_method,
                    status = :status,
                    shipping_date = :shipping_date,
                    expected_arrival_date = :expected_arrival_date,
                    arrival_date = :arrival_date,
                    offer_price_eur = :offer_price_eur,
                    cost_eur = :cost_eur,
                    volume_m3 = :volume_m3,
                    gross_weight_kg = :gross_weight_kg,
                    pallet_count = :pallet_count,
                    notes = :notes,
                    excel_path = :excel_path,
                    excel_original_filename = :excel_original_filename,
                    moved_to_delivered_on = :moved_to_delivered_on,
                    updated_at = :updated_at
                WHERE id = :id
                """, parameters(delivery).addValue("id", delivery.id()));
        return rows == 1;
    }

    public Optional<Delivery> findById(long id) {
        List<Delivery> found = jdbc.query("SELECT " + COLUMNS + " FROM delivery WHERE id = :id",
                new MapSqlParameterSource("id", id), DELIVERY_MAPPER);
        return found.stream().findFirst();
    }

    public List<Delivery> findByTransportMethod(TransportMethod method) {
        return jdbc.query("SELECT " + COLUMNS + " FROM delivery WHERE transport_method = :transport_method",
                new MapSqlParameterSource("transport_method", method.name()), DELIVERY_MAPPER);
    }

    /**
     * F-27: every delivery not DELIVERED, plus DELIVERED ones moved there from
     * {@code deliveredFrom} to {@code today}; newest update first.
     */
    public List<Delivery> findOnBoard(LocalDate deliveredFrom, LocalDate today) {
        return jdbc.query("SELECT " + COLUMNS + """
                FROM delivery
                WHERE status <> :delivered
                   OR (moved_to_delivered_on >= :delivered_from
                       AND moved_to_delivered_on <= :today)
                ORDER BY updated_at DESC, id DESC
                """, new MapSqlParameterSource()
                        .addValue("delivered", DeliveryStatus.DELIVERED.name())
                        .addValue("delivered_from", Columns.date(deliveredFrom))
                        .addValue("today", Columns.date(today)),
                DELIVERY_MAPPER);
    }

    public boolean deleteById(long id) {
        return jdbc.update("DELETE FROM delivery WHERE id = :id",
                new MapSqlParameterSource("id", id)) == 1;
    }

    public boolean exists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM delivery WHERE id = :id",
                new MapSqlParameterSource("id", id), Integer.class);
        return count != null && count > 0;
    }

    public List<DeliverySummary> search(DeliveryQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.customer_company, d.origin_country, d.origin_city,
                       d.destination_country, d.destination_city, d.transport_method, d.status,
                       d.shipping_date, d.arrival_date, d.cost_eur, d.volume_m3,
                       d.gross_weight_kg, d.pallet_count,
                       CASE WHEN d.notes IS NOT NULL AND TRIM(d.notes) <> '' THEN 1 ELSE 0 END
                           AS has_notes,
                       (SELECT COUNT(*) FROM delivery_pdf p WHERE p.delivery_id = d.id)
                           AS pdf_count
                FROM delivery d
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();

        addEquals(clauses, params, "d.customer_company", "customer_company", query.customerCompany());
        addEquals(clauses, params, "d.origin_country", "origin_country", query.originCountry());
        addEquals(clauses, params, "d.origin_city", "origin_city", query.originCity());
        addEquals(clauses, params, "d.destination_country", "destination_country",
                query.destinationCountry());
        addEquals(clauses, params, "d.destination_city", "destination_city", query.destinationCity());
        if (query.transportMethod() != null) {
            clauses.add("d.transport_method = :transport_method");
            params.addValue("transport_method", query.transportMethod().name());
        }
        if (!query.statuses().isEmpty()) {
            clauses.add("d.status IN (:statuses)");
            params.addValue("statuses", query.statuses().stream().map(Enum::name).toList());
        }
        if (query.shippingDateFrom() != null) {
            clauses.add("d.shipping_date >= :shipping_date_from");
            params.addValue("shipping_date_from", Columns.date(query.shippingDateFrom()));
        }
        if (query.shippingDateTo() != null) {
            clauses.add("d.shipping_date <= :shipping_date_to");
            params.addValue("shipping_date_to", Columns.date(query.shippingDateTo()));
        }
        if (query.arrivalDateFrom() != null) {
            clauses.add("d.arrival_date >= :arrival_date_from");
            params.addValue("arrival_date_from", Columns.date(query.arrivalDateFrom()));
        }
        if (query.arrivalDateTo() != null) {
            clauses.add("d.arrival_date <= :arrival_date_to");
            params.addValue("arrival_date_to", Columns.date(query.arrivalDateTo()));
        }
        if (query.costMin() != null) {
            clauses.add("d.cost_eur >= :cost_min");
            params.addValue("cost_min", query.costMin());
        }
        if (query.costMax() != null) {
            clauses.add("d.cost_eur <= :cost_max");
            params.addValue("cost_max", query.costMax());
        }

        if (!clauses.isEmpty()) {
            sql.append("WHERE ").append(String.join("\n  AND ", clauses)).append('\n');
        }

        // F-11: a missing value sorts last in either order.
        String column = "d." + query.sort().column();
        String direction = query.ascending() ? "ASC" : "DESC";
        sql.append("ORDER BY ").append(column).append(" IS NULL, ").append(column).append(' ')
                .append(direction).append(", d.id DESC");

        return jdbc.query(sql.toString(), params, SUMMARY_MAPPER);
    }

    private static void addEquals(List<String> clauses, MapSqlParameterSource params,
            String column, String parameter, String value) {
        if (value != null && !value.isBlank()) {
            clauses.add(column + " = :" + parameter);
            params.addValue(parameter, value);
        }
    }

    private static MapSqlParameterSource parameters(Delivery d) {
        return new MapSqlParameterSource()
                .addValue("customer_company", d.customerCompany())
                .addValue("origin_country", d.originCountry())
                .addValue("origin_city", d.originCity())
                .addValue("origin_postal_code", d.originPostalCode())
                .addValue("origin_street", d.originStreet())
                .addValue("destination_country", d.destinationCountry())
                .addValue("destination_city", d.destinationCity())
                .addValue("destination_postal_code", d.destinationPostalCode())
                .addValue("destination_street", d.destinationStreet())
                .addValue("transport_method", d.transportMethod() == null
                        ? null : d.transportMethod().name())
                .addValue("status", d.status().name())
                .addValue("shipping_date", Columns.date(d.shippingDate()))
                .addValue("expected_arrival_date", Columns.date(d.expectedArrivalDate()))
                .addValue("arrival_date", Columns.date(d.arrivalDate()))
                .addValue("offer_price_eur", d.offerPriceEur())
                .addValue("cost_eur", d.costEur())
                .addValue("volume_m3", d.volumeM3())
                .addValue("gross_weight_kg", d.grossWeightKg())
                .addValue("pallet_count", d.palletCount())
                .addValue("notes", d.notes())
                .addValue("excel_path", d.excelPath())
                .addValue("excel_original_filename", d.excelOriginalFilename())
                .addValue("moved_to_delivered_on", Columns.date(d.movedToDeliveredOn()))
                .addValue("created_at", Columns.timestamp(d.createdAt()))
                .addValue("updated_at", Columns.timestamp(d.updatedAt()));
    }

    private static final RowMapper<Delivery> DELIVERY_MAPPER = (ResultSet rs, int row) ->
            new Delivery(
                    rs.getLong("id"),
                    rs.getString("customer_company"),
                    rs.getString("origin_country"),
                    rs.getString("origin_city"),
                    rs.getString("origin_postal_code"),
                    rs.getString("origin_street"),
                    rs.getString("destination_country"),
                    rs.getString("destination_city"),
                    rs.getString("destination_postal_code"),
                    rs.getString("destination_street"),
                    method(rs),
                    status(rs),
                    Columns.date(rs, "shipping_date"),
                    Columns.date(rs, "expected_arrival_date"),
                    Columns.date(rs, "arrival_date"),
                    rs.getBigDecimal("offer_price_eur"),
                    rs.getBigDecimal("cost_eur"),
                    rs.getBigDecimal("volume_m3"),
                    rs.getBigDecimal("gross_weight_kg"),
                    rs.getBigDecimal("pallet_count"),
                    rs.getString("notes"),
                    rs.getString("excel_path"),
                    rs.getString("excel_original_filename"),
                    Columns.date(rs, "moved_to_delivered_on"),
                    Columns.timestamp(rs, "created_at"),
                    Columns.timestamp(rs, "updated_at"));

    private static final RowMapper<DeliverySummary> SUMMARY_MAPPER = (ResultSet rs, int row) ->
            new DeliverySummary(
                    rs.getLong("id"),
                    rs.getString("customer_company"),
                    rs.getString("origin_country"),
                    rs.getString("origin_city"),
                    rs.getString("destination_country"),
                    rs.getString("destination_city"),
                    method(rs),
                    status(rs),
                    Columns.date(rs, "shipping_date"),
                    Columns.date(rs, "arrival_date"),
                    rs.getBigDecimal("cost_eur"),
                    rs.getBigDecimal("volume_m3"),
                    rs.getBigDecimal("gross_weight_kg"),
                    rs.getBigDecimal("pallet_count"),
                    rs.getInt("has_notes") == 1,
                    rs.getInt("pdf_count"));

    private static TransportMethod method(ResultSet rs) throws SQLException {
        String raw = rs.getString("transport_method");
        return raw == null ? null : TransportMethod.valueOf(raw);
    }

    private static DeliveryStatus status(ResultSet rs) throws SQLException {
        return DeliveryStatus.valueOf(rs.getString("status"));
    }
}
