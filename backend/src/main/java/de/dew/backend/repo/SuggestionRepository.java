package de.dew.backend.repo;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SuggestionRepository {

    private static final Map<String, String> COUNTRY_COLUMNS = Map.of(
            "origin", "origin_country",
            "destination", "destination_country");

    private static final Map<String, String> CITY_COLUMNS = Map.of(
            "origin", "origin_city",
            "destination", "destination_city");

    private final NamedParameterJdbcTemplate jdbc;

    public SuggestionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> companies() {
        return distinct("customer_company", null, null);
    }

    public List<String> countries(String field) {
        return distinct(column(COUNTRY_COLUMNS, field), null, null);
    }

    public List<String> cities(String field, String country) {
        String countryColumn = column(COUNTRY_COLUMNS, field);
        return distinct(column(CITY_COLUMNS, field),
                country == null || country.isBlank() ? null : countryColumn, country);
    }

    private List<String> distinct(String column, String filterColumn, String filterValue) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT ").append(column)
                .append(" AS value FROM delivery WHERE ").append(column)
                .append(" IS NOT NULL AND TRIM(").append(column).append(") <> ''");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filterColumn != null) {
            sql.append(" AND ").append(filterColumn).append(" = :filter_value");
            params.addValue("filter_value", filterValue);
        }
        sql.append(" ORDER BY value");
        return jdbc.queryForList(sql.toString(), params, String.class);
    }

    private static String column(Map<String, String> columns, String field) {
        String column = field == null ? null : columns.get(field);
        if (column == null) {
            throw new IllegalArgumentException("Unknown field: " + field);
        }
        return column;
    }

    public static List<String> allowedFields() {
        return List.of("origin", "destination");
    }
}
