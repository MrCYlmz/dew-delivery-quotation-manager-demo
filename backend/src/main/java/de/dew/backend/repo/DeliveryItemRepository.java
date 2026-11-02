package de.dew.backend.repo;

import de.dew.backend.domain.DeliveryItem;
import java.sql.ResultSet;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryItemRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DeliveryItemRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DeliveryItem> findByDeliveryId(long deliveryId) {
        return jdbc.query("""
                SELECT id, delivery_id, source_row, description, article_no, length_mm, width_mm,
                       height_mm, pallet_count, volume_m3, gross_weight_kg, net_weight_kg,
                       volume_mismatch
                FROM delivery_item
                WHERE delivery_id = :delivery_id
                ORDER BY source_row, id
                """, new MapSqlParameterSource("delivery_id", deliveryId), MAPPER);
    }

    public void replaceAll(long deliveryId, List<DeliveryItem> items) {
        deleteByDeliveryId(deliveryId);
        if (items.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = items.stream()
                .map(item -> (SqlParameterSource) new MapSqlParameterSource()
                        .addValue("delivery_id", deliveryId)
                        .addValue("source_row", item.sourceRow())
                        .addValue("description", item.description())
                        .addValue("article_no", item.articleNo())
                        .addValue("length_mm", item.lengthMm())
                        .addValue("width_mm", item.widthMm())
                        .addValue("height_mm", item.heightMm())
                        .addValue("pallet_count", item.palletCount())
                        .addValue("volume_m3", item.volumeM3())
                        .addValue("gross_weight_kg", item.grossWeightKg())
                        .addValue("net_weight_kg", item.netWeightKg())
                        .addValue("volume_mismatch", item.volumeMismatch() ? 1 : 0))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO delivery_item (delivery_id, source_row, description, article_no,
                    length_mm, width_mm, height_mm, pallet_count, volume_m3, gross_weight_kg,
                    net_weight_kg, volume_mismatch)
                VALUES (:delivery_id, :source_row, :description, :article_no, :length_mm,
                    :width_mm, :height_mm, :pallet_count, :volume_m3, :gross_weight_kg,
                    :net_weight_kg, :volume_mismatch)
                """, batch);
    }

    public int deleteByDeliveryId(long deliveryId) {
        return jdbc.update("DELETE FROM delivery_item WHERE delivery_id = :delivery_id",
                new MapSqlParameterSource("delivery_id", deliveryId));
    }

    private static final RowMapper<DeliveryItem> MAPPER = (ResultSet rs, int row) ->
            new DeliveryItem(
                    rs.getLong("id"),
                    rs.getLong("delivery_id"),
                    rs.getInt("source_row"),
                    rs.getString("description"),
                    rs.getString("article_no"),
                    rs.getBigDecimal("length_mm"),
                    rs.getBigDecimal("width_mm"),
                    rs.getBigDecimal("height_mm"),
                    rs.getBigDecimal("pallet_count"),
                    rs.getBigDecimal("volume_m3"),
                    rs.getBigDecimal("gross_weight_kg"),
                    rs.getBigDecimal("net_weight_kg"),
                    rs.getInt("volume_mismatch") == 1);
}
