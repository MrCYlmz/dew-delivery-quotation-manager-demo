package de.dew.backend.domain;

import java.math.BigDecimal;

public enum TransportMethod {
    SEA(1000),
    AIR(167),
    ROAD(333),
    // F-20: rail as road until Alice confirms her partners' ratio.
    RAIL(333);

    private final BigDecimal kgPerM3;

    TransportMethod(int kgPerM3) {
        this.kgPerM3 = BigDecimal.valueOf(kgPerM3);
    }

    /** F-20: null when neither total is given; a total of 0 counts as not given. */
    public BigDecimal chargeableKg(BigDecimal volumeM3, BigDecimal grossWeightKg) {
        BigDecimal volumetricKg = isGiven(volumeM3) ? volumeM3.multiply(kgPerM3) : null;
        BigDecimal grossKg = isGiven(grossWeightKg) ? grossWeightKg : null;
        if (volumetricKg == null) {
            return grossKg;
        }
        return grossKg == null ? volumetricKg : volumetricKg.max(grossKg);
    }

    private static boolean isGiven(BigDecimal total) {
        return total != null && total.signum() > 0;
    }
}
