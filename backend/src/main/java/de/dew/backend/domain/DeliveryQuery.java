package de.dew.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record DeliveryQuery(
        String customerCompany,
        String originCountry,
        String originCity,
        String destinationCountry,
        String destinationCity,
        TransportMethod transportMethod,
        Set<DeliveryStatus> statuses,
        LocalDate shippingDateFrom,
        LocalDate shippingDateTo,
        LocalDate arrivalDateFrom,
        LocalDate arrivalDateTo,
        BigDecimal costMin,
        BigDecimal costMax,
        SortKey sort,
        boolean ascending) {

    public enum SortKey {
        SHIPPING_DATE("shipping_date"),
        ARRIVAL_DATE("arrival_date"),
        COST_EUR("cost_eur"),
        VOLUME_M3("volume_m3"),
        GROSS_WEIGHT_KG("gross_weight_kg");

        private final String wireName;

        SortKey(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public String column() {
            return wireName;
        }

        public static SortKey fromWire(String value) {
            for (SortKey key : values()) {
                if (key.wireName.equals(value)) {
                    return key;
                }
            }
            throw new IllegalArgumentException("Unknown sort key: " + value);
        }

        public static Map<String, String> allowed() {
            Map<String, String> map = new LinkedHashMap<>();
            for (SortKey key : values()) {
                map.put(key.wireName, key.column());
            }
            return map;
        }
    }

    public static DeliveryQuery unfiltered() {
        return new DeliveryQuery(null, null, null, null, null, null, Set.of(), null, null, null,
                null, null, null, SortKey.SHIPPING_DATE, false);
    }

    public static boolean parseAscending(String order) {
        if (order == null || order.isBlank()) {
            return false;
        }
        return switch (order.toLowerCase(Locale.ROOT)) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw new IllegalArgumentException("Unknown order: " + order);
        };
    }
}
