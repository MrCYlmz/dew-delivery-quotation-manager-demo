package de.dew.backend.error;

import java.util.Map;

public class ValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ValidationException(Map<String, String> fields) {
        super("Validation failed: " + fields);
        this.fields = Map.copyOf(fields);
    }

    public ValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> fields() {
        return fields;
    }
}
