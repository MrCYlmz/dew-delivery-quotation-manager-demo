package de.dew.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.dew.backend.domain.Delivery;
import de.dew.backend.domain.DeliveryItem;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.domain.TransportMethod;
import de.dew.backend.web.dto.DeliveryDetailDto.Totals;
import de.dew.backend.web.dto.DeliveryDetailDto.TotalsEdited;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TotalsReconcilerTest {

    private static final List<DeliveryItem> SAMPLE = List.of(
            item("2.02", "480", "2"),
            item(null, null, null),
            item("5.40", "1100", "4"));

    @Test
    void sumsItemsTreatingBlankAsZero() {
        Totals sums = TotalsReconciler.itemSums(SAMPLE);

        assertThat(sums.volumeM3()).isEqualByComparingTo("7.42");
        assertThat(sums.grossWeightKg()).isEqualByComparingTo("1580");
        assertThat(sums.palletCount()).isEqualByComparingTo("6");
    }

    @Test
    void noItemsSumToZero() {
        Totals sums = TotalsReconciler.itemSums(List.of());
        assertThat(sums.volumeM3()).isEqualByComparingTo("0");
        assertThat(sums.grossWeightKg()).isEqualByComparingTo("0");
        assertThat(sums.palletCount()).isEqualByComparingTo("0");
    }

    @Test
    void savedTotalsMatchingTheSumsAreNotMarkedEdited() {
        Delivery delivery = deliveryWith("7.42", "1580", "6");
        TotalsEdited edited = TotalsReconciler.totalsEdited(delivery,
                TotalsReconciler.itemSums(SAMPLE), SAMPLE);

        assertThat(edited).isEqualTo(new TotalsEdited(false, false, false));
    }

    @Test
    void onlyTheOverriddenTotalIsMarked() {
        Delivery delivery = deliveryWith("14.00", "1580", "6");
        TotalsEdited edited = TotalsReconciler.totalsEdited(delivery,
                TotalsReconciler.itemSums(SAMPLE), SAMPLE);

        assertThat(edited.volumeM3()).isTrue();
        assertThat(edited.grossWeightKg()).isFalse();
        assertThat(edited.palletCount()).isFalse();
    }

    @Test
    void aDifferenceOfScaleAloneIsNotAnEdit() {
        assertThat(new BigDecimal("7.420")).isNotEqualTo(new BigDecimal("7.42"));

        Delivery delivery = deliveryWith("7.420", "1580.00", "6.0");
        TotalsEdited edited = TotalsReconciler.totalsEdited(delivery,
                TotalsReconciler.itemSums(SAMPLE), SAMPLE);

        assertThat(edited).isEqualTo(new TotalsEdited(false, false, false));
    }

    @Test
    void nullSavedTotalsAreNotMarked() {
        Delivery delivery = deliveryWith(null, null, null);
        TotalsEdited edited = TotalsReconciler.totalsEdited(delivery,
                TotalsReconciler.itemSums(SAMPLE), SAMPLE);

        assertThat(edited).isEqualTo(new TotalsEdited(false, false, false));
    }

    @Test
    void withNoItemsNothingIsMarkedEvenWhenTotalsArePresent() {
        Delivery delivery = deliveryWith("7.42", "1580", "6");
        TotalsEdited edited = TotalsReconciler.totalsEdited(delivery,
                TotalsReconciler.itemSums(List.of()), List.of());

        assertThat(edited).isEqualTo(new TotalsEdited(false, false, false));
    }

    @Test
    void nullSumsMeanNothingIsEdited() {
        Delivery delivery = deliveryWith("7.42", "1580", "6");
        assertThat(TotalsReconciler.totalsEdited(delivery, null, SAMPLE))
                .isEqualTo(TotalsReconciler.NOTHING_EDITED);
    }

    @Test
    void fractionalPalletCountsSumCorrectly() {
        List<DeliveryItem> items = List.of(item("1.5", "10", "0.5"), item("1.5", "10", "0.25"));
        assertThat(TotalsReconciler.itemSums(items).palletCount()).isEqualByComparingTo("0.75");
    }

    private static DeliveryItem item(String volume, String gross, String pallets) {
        return new DeliveryItem(null, 1L, 5, "Chair", "A-1", null, null, null, bd(pallets),
                bd(volume), bd(gross), null, false);
    }

    private static Delivery deliveryWith(String volume, String gross, String pallets) {
        return new Delivery(1L, "Example Interiors", "CN", "Ningbo", null, null, "DE", "Munich", null,
                null, TransportMethod.SEA, DeliveryStatus.TO_DO, LocalDate.of(2026, 7, 4), null, null,
                null, new BigDecimal("4210.00"), bd(volume), bd(gross), bd(pallets), null, null,
                null, null, null, null);
    }

    private static BigDecimal bd(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
