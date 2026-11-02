package de.dew.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record Delivery(
        Long id,
        String customerCompany,
        String originCountry,
        String originCity,
        String originPostalCode,
        String originStreet,
        String destinationCountry,
        String destinationCity,
        String destinationPostalCode,
        String destinationStreet,
        TransportMethod transportMethod,
        DeliveryStatus status,
        LocalDate shippingDate,
        LocalDate expectedArrivalDate,
        LocalDate arrivalDate,
        BigDecimal offerPriceEur,
        BigDecimal costEur,
        BigDecimal volumeM3,
        BigDecimal grossWeightKg,
        BigDecimal palletCount,
        String notes,
        String excelPath,
        String excelOriginalFilename,
        LocalDate movedToDeliveredOn,
        Instant createdAt,
        Instant updatedAt) {

    public Delivery withId(Long newId) {
        return new Delivery(newId, customerCompany, originCountry, originCity, originPostalCode,
                originStreet, destinationCountry, destinationCity, destinationPostalCode,
                destinationStreet, transportMethod, status, shippingDate, expectedArrivalDate,
                arrivalDate, offerPriceEur, costEur, volumeM3, grossWeightKg, palletCount, notes,
                excelPath, excelOriginalFilename, movedToDeliveredOn, createdAt, updatedAt);
    }

    public Delivery withExcel(String path, String originalFilename) {
        return new Delivery(id, customerCompany, originCountry, originCity, originPostalCode,
                originStreet, destinationCountry, destinationCity, destinationPostalCode,
                destinationStreet, transportMethod, status, shippingDate, expectedArrivalDate,
                arrivalDate, offerPriceEur, costEur, volumeM3, grossWeightKg, palletCount, notes,
                path, originalFilename, movedToDeliveredOn, createdAt, updatedAt);
    }

    public Delivery withStatus(DeliveryStatus newStatus, LocalDate newShippingDate,
            LocalDate newExpectedArrivalDate, LocalDate newArrivalDate,
            LocalDate newMovedToDeliveredOn, Instant newUpdatedAt) {
        return new Delivery(id, customerCompany, originCountry, originCity, originPostalCode,
                originStreet, destinationCountry, destinationCity, destinationPostalCode,
                destinationStreet, transportMethod, newStatus, newShippingDate,
                newExpectedArrivalDate, newArrivalDate, offerPriceEur, costEur, volumeM3,
                grossWeightKg, palletCount, notes, excelPath, excelOriginalFilename,
                newMovedToDeliveredOn, createdAt, newUpdatedAt);
    }
}
