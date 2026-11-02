package de.dew.backend.web;

import de.dew.backend.domain.DeliveryQuery;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.domain.TransportMethod;
import de.dew.backend.error.ValidationException;
import de.dew.backend.service.DeliveryService;
import de.dew.backend.web.dto.BoardCardDto;
import de.dew.backend.web.dto.DeliveryDetailDto;
import de.dew.backend.web.dto.DeliveryListRowDto;
import de.dew.backend.web.dto.DeliveryRequest;
import de.dew.backend.web.dto.StatusMoveRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeliveryListRowDto> list(
            @RequestParam(name = "customer_company", required = false) String customerCompany,
            @RequestParam(name = "origin_country", required = false) String originCountry,
            @RequestParam(name = "origin_city", required = false) String originCity,
            @RequestParam(name = "destination_country", required = false) String destinationCountry,
            @RequestParam(name = "destination_city", required = false) String destinationCity,
            @RequestParam(name = "transport_method", required = false) TransportMethod transportMethod,
            @RequestParam(name = "status", required = false) List<String> status,
            @RequestParam(name = "shipping_date_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shippingDateFrom,
            @RequestParam(name = "shipping_date_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shippingDateTo,
            @RequestParam(name = "arrival_date_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivalDateFrom,
            @RequestParam(name = "arrival_date_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivalDateTo,
            @RequestParam(name = "cost_min", required = false) BigDecimal costMin,
            @RequestParam(name = "cost_max", required = false) BigDecimal costMax,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "order", required = false) String order) {

        DeliveryQuery.SortKey sortKey = DeliveryQuery.SortKey.SHIPPING_DATE;
        if (sort != null && !sort.isBlank()) {
            try {
                sortKey = DeliveryQuery.SortKey.fromWire(sort);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("sort",
                        "must be one of " + DeliveryQuery.SortKey.allowed().keySet());
            }
        }
        boolean ascending;
        try {
            ascending = DeliveryQuery.parseAscending(order);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("order", "must be asc or desc");
        }

        return service.list(new DeliveryQuery(customerCompany, originCountry, originCity,
                destinationCountry, destinationCity, transportMethod, statuses(status),
                shippingDateFrom, shippingDateTo, arrivalDateFrom, arrivalDateTo, costMin, costMax,
                sortKey, ascending));
    }

    @PostMapping
    public ResponseEntity<DeliveryDetailDto> create(@Valid @RequestBody DeliveryRequest request) {
        DeliveryDetailDto created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public DeliveryDetailDto detail(@PathVariable("id") long id) {
        return service.detail(id);
    }

    @PutMapping("/{id}")
    public DeliveryDetailDto update(@PathVariable("id") long id,
            @Valid @RequestBody DeliveryRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/status")
    public BoardCardDto move(@PathVariable("id") long id,
            @Valid @RequestBody StatusMoveRequest request) {
        return service.move(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** F-12: any of the given statuses; a blank value is ignored, none means every status. */
    private static Set<DeliveryStatus> statuses(List<String> values) {
        Set<DeliveryStatus> statuses = EnumSet.noneOf(DeliveryStatus.class);
        if (values == null) {
            return statuses;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                statuses.add(DeliveryStatus.valueOf(value));
            } catch (IllegalArgumentException e) {
                throw new ValidationException("status",
                        "must be one of " + List.of(DeliveryStatus.values()));
            }
        }
        return statuses;
    }
}
