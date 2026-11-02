package de.dew.backend.web;

import de.dew.backend.domain.PotentialDelivery;
import de.dew.backend.domain.TransportMethod;
import de.dew.backend.error.ValidationException;
import de.dew.backend.service.EstimateService;
import de.dew.backend.web.dto.EstimateComparablesDto;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/estimate")
public class EstimateController {

    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Za-z]{2}");

    private final EstimateService service;

    public EstimateController(EstimateService service) {
        this.service = service;
    }

    // Required params are bound as optional so a missing and a blank value both get the
    // same "required" field error; Spring turns a blank enum value into null.
    @GetMapping("/comparables")
    public EstimateComparablesDto comparables(
            @RequestParam(name = "transport_method", required = false) TransportMethod transportMethod,
            @RequestParam(name = "origin_country", required = false) String originCountry,
            @RequestParam(name = "destination_country", required = false) String destinationCountry,
            @RequestParam(name = "volume_m3", required = false) BigDecimal volumeM3,
            @RequestParam(name = "gross_weight_kg", required = false) BigDecimal grossWeightKg) {

        Map<String, String> fields = new LinkedHashMap<>();
        if (transportMethod == null) {
            fields.put("transport_method", "required");
        }
        checkCountry(fields, "origin_country", originCountry);
        checkCountry(fields, "destination_country", destinationCountry);
        if (volumeM3 != null && volumeM3.signum() <= 0) {
            fields.put("volume_m3", "must be greater than 0");
        }
        if (grossWeightKg != null && grossWeightKg.signum() <= 0) {
            fields.put("gross_weight_kg", "must be greater than 0");
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        return service.comparables(new PotentialDelivery(transportMethod, countryCode(originCountry),
                countryCode(destinationCountry), volumeM3, grossWeightKg));
    }

    private static void checkCountry(Map<String, String> fields, String field, String country) {
        if (country == null || country.isBlank()) {
            fields.put(field, "required");
        } else if (!COUNTRY_CODE.matcher(country.strip()).matches()) {
            fields.put(field, "must be a two-letter country code");
        }
    }

    // Saved deliveries hold upper-case codes, so a lower-case code still finds its route.
    private static String countryCode(String country) {
        return country.strip().toUpperCase(Locale.ROOT);
    }
}
