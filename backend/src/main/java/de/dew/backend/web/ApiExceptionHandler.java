package de.dew.backend.web;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import de.dew.backend.error.NotFoundException;
import de.dew.backend.error.ValidationException;
import de.dew.backend.web.dto.ErrorResponse;
import de.dew.parser.PackingListParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> onInvalidBody(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(snakeCase(error.getField()),
                    error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage());
        }
        if (fields.isEmpty()) {
            fields.put("body", "invalid");
        }
        return ResponseEntity.badRequest().body(ErrorResponse.validation(fields));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> onValidation(ValidationException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.validation(e.fields()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> onUnreadableBody(HttpMessageNotReadableException e) {
        String field = "body";
        String message = "malformed";
        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String name = mismatch.getPath().get(mismatch.getPath().size() - 1).getFieldName();
            if (name != null) {
                field = snakeCase(name);
                message = e.getCause() instanceof InvalidFormatException ? "invalid value" : "invalid";
            }
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(Map.of(field, message)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> onBadParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(Map.of(e.getName(), "invalid value")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> onMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(Map.of(e.getParameterName(), "required")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> onMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(Map.of(e.getRequestPartName(), "required")));
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> onUploadTooLarge(MultipartException e) {
        log.warn("Rejected multipart request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(Map.of("files", "upload rejected: " + rootMessage(e))));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> onNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("not_found", e.getMessage()));
    }

    @ExceptionHandler(PackingListParseException.class)
    public ResponseEntity<ErrorResponse> onParseFailure(PackingListParseException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("parse_failed", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onUnexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse spring) {
            HttpStatus status = HttpStatus.valueOf(spring.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                return ResponseEntity.status(status).body(ErrorResponse.of("not_found", e.getMessage()));
            }
            return ResponseEntity.status(status)
                    .body(ErrorResponse.of(status.name().toLowerCase(Locale.ROOT), e.getMessage()));
        }
        log.error("Unhandled failure", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("internal", rootMessage(e)));
    }

    static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
