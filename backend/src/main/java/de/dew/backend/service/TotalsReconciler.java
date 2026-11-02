package de.dew.backend.service;

import de.dew.backend.domain.Delivery;
import de.dew.backend.domain.DeliveryItem;
import de.dew.backend.web.dto.DeliveryDetailDto.Totals;
import de.dew.backend.web.dto.DeliveryDetailDto.TotalsEdited;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public final class TotalsReconciler {

    public static final TotalsEdited NOTHING_EDITED = new TotalsEdited(false, false, false);

    private TotalsReconciler() {
    }

    public static Totals itemSums(List<DeliveryItem> items) {
        return new Totals(
                sum(items, DeliveryItem::volumeM3),
                sum(items, DeliveryItem::grossWeightKg),
                sum(items, DeliveryItem::palletCount));
    }

    public static TotalsEdited totalsEdited(Delivery delivery, Totals sums, List<DeliveryItem> items) {
        if (sums == null || items.isEmpty()) {
            return NOTHING_EDITED;
        }
        return new TotalsEdited(
                differs(delivery.volumeM3(), sums.volumeM3()),
                differs(delivery.grossWeightKg(), sums.grossWeightKg()),
                differs(delivery.palletCount(), sums.palletCount()));
    }

    private static boolean differs(BigDecimal saved, BigDecimal sum) {
        if (saved == null) {
            return false;
        }
        return saved.compareTo(sum) != 0;
    }

    private static BigDecimal sum(List<DeliveryItem> items, Function<DeliveryItem, BigDecimal> field) {
        BigDecimal total = BigDecimal.ZERO;
        for (DeliveryItem item : items) {
            BigDecimal value = field.apply(item);
            if (value != null) {
                total = total.add(value);
            }
        }
        return total;
    }
}
