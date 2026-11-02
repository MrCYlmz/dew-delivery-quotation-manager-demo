package de.dew.backend.repo;

import de.dew.backend.domain.DeliveryPdf;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryPdfRepository {

    private static final String COLUMNS =
            "id, delivery_id, original_filename, stored_path, size_bytes, sort_order, uploaded_at";

    private final NamedParameterJdbcTemplate jdbc;

    public DeliveryPdfRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DeliveryPdf insert(DeliveryPdf pdf) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO delivery_pdf (delivery_id, original_filename, stored_path, size_bytes,
                    sort_order, uploaded_at)
                VALUES (:delivery_id, :original_filename, :stored_path, :size_bytes, :sort_order,
                    :uploaded_at)
                """, new MapSqlParameterSource()
                        .addValue("delivery_id", pdf.deliveryId())
                        .addValue("original_filename", pdf.originalFilename())
                        .addValue("stored_path", pdf.storedPath())
                        .addValue("size_bytes", pdf.sizeBytes())
                        .addValue("sort_order", pdf.sortOrder())
                        .addValue("uploaded_at", Columns.timestamp(pdf.uploadedAt())),
                keys, new String[] {"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert returned no generated key for delivery_pdf");
        }
        return new DeliveryPdf(key.longValue(), pdf.deliveryId(), pdf.originalFilename(),
                pdf.storedPath(), pdf.sizeBytes(), pdf.sortOrder(), pdf.uploadedAt());
    }

    public List<DeliveryPdf> findByDeliveryId(long deliveryId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM delivery_pdf"
                + " WHERE delivery_id = :delivery_id ORDER BY sort_order, id",
                new MapSqlParameterSource("delivery_id", deliveryId), MAPPER);
    }

    public Optional<DeliveryPdf> find(long deliveryId, long pdfId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM delivery_pdf"
                + " WHERE id = :id AND delivery_id = :delivery_id",
                new MapSqlParameterSource("id", pdfId).addValue("delivery_id", deliveryId), MAPPER)
                .stream().findFirst();
    }

    public int nextSortOrder(long deliveryId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1) FROM delivery_pdf WHERE delivery_id = :delivery_id",
                new MapSqlParameterSource("delivery_id", deliveryId), Integer.class);
        return max == null ? 0 : max + 1;
    }

    public boolean delete(long deliveryId, long pdfId) {
        return jdbc.update("DELETE FROM delivery_pdf WHERE id = :id AND delivery_id = :delivery_id",
                new MapSqlParameterSource("id", pdfId).addValue("delivery_id", deliveryId)) == 1;
    }

    private static final RowMapper<DeliveryPdf> MAPPER = (ResultSet rs, int row) ->
            new DeliveryPdf(
                    rs.getLong("id"),
                    rs.getLong("delivery_id"),
                    rs.getString("original_filename"),
                    rs.getString("stored_path"),
                    Columns.nullableLong(rs, "size_bytes"),
                    rs.getInt("sort_order"),
                    Columns.timestamp(rs, "uploaded_at"));
}
