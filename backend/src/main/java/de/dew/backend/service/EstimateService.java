package de.dew.backend.service;

import de.dew.backend.domain.ComparablesFallback;
import de.dew.backend.domain.Delivery;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.domain.PotentialDelivery;
import de.dew.backend.repo.DeliveryRepository;
import de.dew.backend.repo.SuggestionRepository;
import de.dew.backend.web.dto.EstimateComparablesDto;
import de.dew.backend.web.dto.EstimateComparablesDto.ComparableDeliveryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Picks and ranks the comparables for a potential delivery (F-20 to F-22). Saves nothing. */
@Service
public class EstimateService {

    private static final int MAX_COMPARABLES = 10;
    private static final int MIN_BEFORE_FALLBACK = 3;
    private static final double AGE_WEIGHT_PER_YEAR = 0.5;
    private static final double DAYS_PER_YEAR = 365.25;
    private static final String CUSTOMER_PLACEHOLDER = "[customer]";

    private final DeliveryRepository deliveries;
    private final SuggestionRepository suggestions;
    private final Clock clock;

    public EstimateService(DeliveryRepository deliveries, SuggestionRepository suggestions,
            Clock clock) {
        this.deliveries = deliveries;
        this.suggestions = suggestions;
        this.clock = clock;
    }

    public EstimateComparablesDto comparables(PotentialDelivery potential) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal chargeableKg = potential.chargeableKg();

        List<Delivery> candidates = deliveries.findByTransportMethod(potential.transportMethod())
                .stream()
                .filter(d -> d.status() != DeliveryStatus.TO_DO)
                .filter(d -> d.costEur() != null || d.offerPriceEur() != null)
                .filter(d -> chargeableKg == null || chargeableKg(d) != null)
                .toList();

        Predicate<Delivery> sameOrigin = d -> d.originCountry().equals(potential.originCountry());
        Predicate<Delivery> sameRoute = sameOrigin
                .and(d -> d.destinationCountry().equals(potential.destinationCountry()));

        ComparablesFallback fallback = ComparablesFallback.NONE;
        List<Delivery> chosen = candidates.stream().filter(sameRoute).toList();
        if (chosen.size() < MIN_BEFORE_FALLBACK) {
            fallback = ComparablesFallback.DESTINATION_IGNORED;
            chosen = candidates.stream().filter(sameOrigin).toList();
        }
        if (chosen.size() < MIN_BEFORE_FALLBACK) {
            fallback = ComparablesFallback.ORIGIN_AND_DESTINATION_IGNORED;
            chosen = candidates;
        }

        Comparator<Delivery> ranking = Comparator
                .comparingDouble((Delivery d) -> score(d, chargeableKg, today))
                .thenComparing((Delivery d) -> shippedOrToday(d, today), Comparator.reverseOrder())
                .thenComparing(Delivery::id, Comparator.reverseOrder());
        List<Delivery> ranked = chosen.stream().sorted(ranking).limit(MAX_COMPARABLES).toList();

        Pattern companyNames = companyNamePattern(suggestions.companies());
        int removedCompanyNames = 0;
        List<ComparableDeliveryDto> comparables = new ArrayList<>();
        for (Delivery d : ranked) {
            MaskedNotes notes = mask(d.notes(), companyNames);
            removedCompanyNames += notes.replacements();
            comparables.add(ComparableDeliveryDto.from(d,
                    (int) ageIn(ChronoUnit.MONTHS, d, today),
                    chargeableKg(d), notes.text()));
        }
        return new EstimateComparablesDto(chargeableKg, fallback, removedCompanyNames, comparables);
    }

    private static BigDecimal chargeableKg(Delivery d) {
        return d.transportMethod().chargeableKg(d.volumeM3(), d.grossWeightKg());
    }

    /** F-21: a delivery not shipped yet is priced from a current offer. */
    private static LocalDate shippedOrToday(Delivery d, LocalDate today) {
        return d.shippingDate() == null ? today : d.shippingDate();
    }

    /** F-21: a shipping date after today, or none, counts as age 0. */
    private static long ageIn(ChronoUnit unit, Delivery d, LocalDate today) {
        return Math.max(0, unit.between(shippedOrToday(d, today), today));
    }

    /** F-21: lower is closer. Without a size on the potential delivery, age alone. */
    private static double score(Delivery d, BigDecimal potentialKg, LocalDate today) {
        double ageYears = ageIn(ChronoUnit.DAYS, d, today) / DAYS_PER_YEAR;
        double score = AGE_WEIGHT_PER_YEAR * ageYears;
        if (potentialKg != null) {
            score += Math.abs(Math.log(chargeableKg(d).doubleValue() / potentialKg.doubleValue()));
        }
        return score;
    }

    /**
     * One alternation, longest name first, so at any position the longest name wins and a
     * shorter name inside it is not counted again (F-22). A whole word means the match is
     * not directly next to a letter or digit; names may start or end with punctuation, so
     * {@code \b} would not do.
     */
    private static Pattern companyNamePattern(List<String> companies) {
        List<String> names = companies.stream()
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        if (names.isEmpty()) {
            return null;
        }
        String alternatives = names.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static MaskedNotes mask(String notes, Pattern companyNames) {
        if (notes == null || companyNames == null) {
            return new MaskedNotes(notes, 0);
        }
        Matcher matcher = companyNames.matcher(notes);
        StringBuilder masked = new StringBuilder();
        int replacements = 0;
        while (matcher.find()) {
            matcher.appendReplacement(masked, Matcher.quoteReplacement(CUSTOMER_PLACEHOLDER));
            replacements++;
        }
        matcher.appendTail(masked);
        return new MaskedNotes(masked.toString(), replacements);
    }

    private record MaskedNotes(String text, int replacements) {
    }
}
