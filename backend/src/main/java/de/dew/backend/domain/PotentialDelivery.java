package de.dew.backend.domain;

import java.math.BigDecimal;

public record PotentialDelivery(
        TransportMethod transportMethod,
        String originCountry,
        String destinationCountry,
        BigDecimal volumeM3,
        BigDecimal grossWeightKg) {

    public BigDecimal chargeableKg() {
        return transportMethod.chargeableKg(volumeM3, grossWeightKg);
    }
}
