package de.dew.backend.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public final class Columns {

    private Columns() {
    }

    public static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new SQLException("Column " + column + " is not an ISO date: " + raw, e);
        }
    }

    public static String timestamp(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static Instant timestamp(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new SQLException("Column " + column + " is not an ISO instant: " + raw, e);
        }
    }

    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
