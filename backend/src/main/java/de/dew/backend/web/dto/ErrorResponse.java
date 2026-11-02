package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("detail") String detail,
        @JsonProperty("fields") Map<String, String> fields) {

    public static ErrorResponse validation(Map<String, String> fields) {
        return new ErrorResponse("validation", null, fields);
    }

    public static ErrorResponse of(String error, String detail) {
        return new ErrorResponse(error, detail, null);
    }
}
