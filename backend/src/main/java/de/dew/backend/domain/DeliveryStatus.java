package de.dew.backend.domain;

/** F-24, in the board's order. */
public enum DeliveryStatus {
    TO_DO,
    OFFERS_RECEIVED,
    OFFER_ACCEPTED,
    ON_THE_WAY,
    DELIVERED;

    /** F-25: once a delivery has left, its shipping date is known. */
    public boolean requiresShippingDate() {
        return this == ON_THE_WAY || this == DELIVERED;
    }

    /** F-25: the real arrival; the expected one is never required. */
    public boolean requiresArrivalDate() {
        return this == DELIVERED;
    }
}
