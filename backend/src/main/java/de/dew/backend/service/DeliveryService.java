package de.dew.backend.service;

import de.dew.backend.domain.Delivery;
import de.dew.backend.domain.DeliveryItem;
import de.dew.backend.domain.DeliveryPdf;
import de.dew.backend.domain.DeliveryQuery;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.error.NotFoundException;
import de.dew.backend.error.ValidationException;
import de.dew.backend.repo.DeliveryItemRepository;
import de.dew.backend.repo.DeliveryPdfRepository;
import de.dew.backend.repo.DeliveryRepository;
import de.dew.backend.storage.FileStorageService;
import de.dew.backend.web.dto.BoardCardDto;
import de.dew.backend.web.dto.DeliveryDetailDto;
import de.dew.backend.web.dto.DeliveryDetailDto.Totals;
import de.dew.backend.web.dto.DeliveryDetailDto.TotalsEdited;
import de.dew.backend.web.dto.DeliveryListRowDto;
import de.dew.backend.web.dto.DeliveryRequest;
import de.dew.backend.web.dto.ItemDto;
import de.dew.backend.web.dto.ParseResultDto;
import de.dew.backend.web.dto.PdfDto;
import de.dew.backend.web.dto.StatusMoveRequest;
import de.dew.parser.PackingListItem;
import de.dew.parser.PackingListParseException;
import de.dew.parser.PackingListParser;
import de.dew.parser.PackingListResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    // F-27: a delivered card shows on the day it was moved and the 6 days after.
    private static final int DAYS_ON_BOARD_AFTER_DELIVERY = 6;

    private final DeliveryRepository deliveries;
    private final DeliveryItemRepository items;
    private final DeliveryPdfRepository pdfs;
    private final FileStorageService storage;
    private final PackingListParser parser;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public DeliveryService(DeliveryRepository deliveries, DeliveryItemRepository items,
            DeliveryPdfRepository pdfs, FileStorageService storage, PackingListParser parser,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.deliveries = deliveries;
        this.items = items;
        this.pdfs = pdfs;
        this.storage = storage;
        this.parser = parser;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public List<DeliveryListRowDto> list(DeliveryQuery query) {
        return deliveries.search(query).stream().map(DeliveryListRowDto::from).toList();
    }

    public DeliveryDetailDto detail(long id) {
        return toDetail(require(id));
    }

    public List<BoardCardDto> board() {
        LocalDate today = LocalDate.now(clock);
        return deliveries.findOnBoard(today.minusDays(DAYS_ON_BOARD_AFTER_DELIVERY), today).stream()
                .map(d -> BoardCardDto.from(d, daysLate(d, today)))
                .toList();
    }

    /** F-27: a delivery created at DELIVERED is archived, not tracked, so it gets no day. */
    public DeliveryDetailDto create(DeliveryRequest request) {
        requireDatesFor(request.status(), request.shippingDate(), request.arrivalDate());
        Instant now = Instant.now();
        Delivery saved = deliveries.insert(fromRequest(null, request, null, now, now, null, null));
        return toDetail(saved);
    }

    public DeliveryDetailDto update(long id, DeliveryRequest request) {
        Delivery existing = require(id);
        requireDatesFor(request.status(), request.shippingDate(), request.arrivalDate());
        Delivery updated = fromRequest(id, request, movedToDeliveredOn(existing, request.status()),
                existing.createdAt(), Instant.now(), existing.excelPath(),
                existing.excelOriginalFilename());
        if (!deliveries.update(updated)) {
            throw NotFoundException.delivery(id);
        }
        return toDetail(updated);
    }

    /** F-29: a date sent replaces the stored one, a date left out keeps it. */
    public BoardCardDto move(long id, StatusMoveRequest request) {
        Delivery existing = require(id);
        LocalDate shippingDate = request.shippingDate() != null
                ? request.shippingDate() : existing.shippingDate();
        LocalDate expectedArrivalDate = request.expectedArrivalDate() != null
                ? request.expectedArrivalDate() : existing.expectedArrivalDate();
        LocalDate arrivalDate = request.arrivalDate() != null
                ? request.arrivalDate() : existing.arrivalDate();
        requireDatesFor(request.status(), shippingDate, arrivalDate);

        Delivery moved = existing.withStatus(request.status(), shippingDate, expectedArrivalDate,
                arrivalDate, movedToDeliveredOn(existing, request.status()), Instant.now());
        if (!deliveries.update(moved)) {
            throw NotFoundException.delivery(id);
        }
        log.info("Delivery {} moved from {} to {}", id, existing.status(), moved.status());
        return BoardCardDto.from(moved, daysLate(moved, LocalDate.now(clock)));
    }

    public void delete(long id) {
        Delivery delivery = require(id);
        List<String> filesToRemove = new ArrayList<>();
        for (DeliveryPdf pdf : pdfs.findByDeliveryId(id)) {
            filesToRemove.add(pdf.storedPath());
        }
        if (delivery.excelPath() != null) {
            filesToRemove.add(delivery.excelPath());
        }

        if (!deliveries.deleteById(id)) {
            throw NotFoundException.delivery(id);
        }
        filesToRemove.forEach(storage::delete);
    }

    public DeliveryDetailDto addPdfs(long id, List<MultipartFile> files) {
        Delivery delivery = require(id);
        if (files == null || files.isEmpty()) {
            throw new ValidationException("files", "at least one file is required");
        }
        List<String> storedThisRequest = new ArrayList<>();
        try {
            transactions.executeWithoutResult(status -> {
                int sortOrder = pdfs.nextSortOrder(id);
                for (MultipartFile file : files) {
                    if (file.isEmpty()) {
                        throw new ValidationException("files",
                                "empty file: " + displayName(file));
                    }
                    String storedPath;
                    try (InputStream in = file.getInputStream()) {
                        storedPath = storage.store(in, file.getOriginalFilename());
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not store uploaded PDFs", e);
                    }
                    storedThisRequest.add(storedPath);
                    pdfs.insert(new DeliveryPdf(null, id, displayName(file), storedPath,
                            file.getSize(), sortOrder++, Instant.now()));
                }
            });
        } catch (RuntimeException e) {
            storedThisRequest.forEach(storage::delete);
            throw e;
        }
        return toDetail(delivery);
    }

    public void deletePdf(long id, long pdfId) {
        DeliveryPdf pdf = requirePdf(id, pdfId);
        if (!pdfs.delete(id, pdfId)) {
            throw NotFoundException.pdf(id, pdfId);
        }
        storage.delete(pdf.storedPath());
    }

    public DeliveryPdf pdf(long id, long pdfId) {
        return requirePdf(id, pdfId);
    }

    public ParseResultDto parseOnly(MultipartFile file) throws PackingListParseException {
        requireFile(file, "file");
        try (InputStream in = file.getInputStream()) {
            return ParseResultDto.from(parser.parse(in, file.getOriginalFilename()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded workbook", e);
        }
    }

    public DeliveryDetailDto replaceExcel(long id, MultipartFile file) throws PackingListParseException {
        Delivery delivery = require(id);
        requireFile(file, "file");

        PackingListResult parsed;
        try (InputStream in = file.getInputStream()) {
            parsed = parser.parse(in, file.getOriginalFilename());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded workbook", e);
        }

        String previousPath = delivery.excelPath();
        String storedPath;
        try (InputStream in = file.getInputStream()) {
            storedPath = storage.store(in, file.getOriginalFilename());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded workbook", e);
        }

        Delivery updated = delivery.withExcel(storedPath, displayName(file));
        try {
            transactions.executeWithoutResult(status -> {
                if (!deliveries.update(updated)) {
                    throw NotFoundException.delivery(id);
                }
                items.replaceAll(id, toItems(id, parsed.items()));
            });
        } catch (RuntimeException e) {
            storage.delete(storedPath);
            throw e;
        }

        if (previousPath != null && !previousPath.equals(storedPath)) {
            storage.delete(previousPath);
        }
        log.info("Delivery {} now carries {} item rows from sheet '{}'", id,
                parsed.items() == null ? 0 : parsed.items().size(), parsed.sheetName());
        return toDetail(updated);
    }

    public Delivery deliveryWithExcel(long id) {
        Delivery delivery = require(id);
        if (delivery.excelPath() == null) {
            throw new NotFoundException("Delivery " + id + " has no Excel attached");
        }
        return delivery;
    }

    public FileStorageService storage() {
        return storage;
    }

    private Delivery require(long id) {
        return deliveries.findById(id).orElseThrow(() -> NotFoundException.delivery(id));
    }

    private DeliveryPdf requirePdf(long id, long pdfId) {
        if (!deliveries.exists(id)) {
            throw NotFoundException.delivery(id);
        }
        return pdfs.find(id, pdfId).orElseThrow(() -> NotFoundException.pdf(id, pdfId));
    }

    /** F-25: only the target status's rules apply; the expected arrival is never required. */
    private static void requireDatesFor(DeliveryStatus status, LocalDate shippingDate,
            LocalDate arrivalDate) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (status.requiresShippingDate() && shippingDate == null) {
            fields.put("shipping_date", "required");
        }
        if (status.requiresArrivalDate() && arrivalDate == null) {
            fields.put("arrival_date", "required");
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
    }

    /** F-30: days past the expected arrival of a delivery on the way, null when not late. */
    private static Integer daysLate(Delivery delivery, LocalDate today) {
        if (delivery.status() != DeliveryStatus.ON_THE_WAY
                || delivery.expectedArrivalDate() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(delivery.expectedArrivalDate(), today);
        return days >= 1 ? (int) days : null;
    }

    /**
     * F-27: set when a delivery becomes DELIVERED, kept while it stays there, cleared when it
     * leaves.
     */
    private LocalDate movedToDeliveredOn(Delivery existing, DeliveryStatus newStatus) {
        if (newStatus != DeliveryStatus.DELIVERED) {
            return null;
        }
        if (existing.status() == DeliveryStatus.DELIVERED) {
            return existing.movedToDeliveredOn();
        }
        return LocalDate.now(clock);
    }

    private static void requireFile(MultipartFile file, String field) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(field, "required");
        }
    }

    private static String displayName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "unnamed" : name;
    }

    private static List<DeliveryItem> toItems(long deliveryId, List<PackingListItem> parsed) {
        if (parsed == null) {
            return List.of();
        }
        return parsed.stream()
                .map(p -> new DeliveryItem(null, deliveryId, p.sourceRow(), p.description(),
                        p.articleNo(), p.lengthMm(), p.widthMm(), p.heightMm(), p.palletCount(),
                        p.volumeM3(), p.grossWeightKg(), p.netWeightKg(), p.volumeMismatch()))
                .toList();
    }

    private static Delivery fromRequest(Long id, DeliveryRequest r, LocalDate movedToDeliveredOn,
            Instant createdAt, Instant updatedAt, String excelPath, String excelOriginalFilename) {
        return new Delivery(id, trimmed(r.customerCompany()), trimmed(r.originCountry()),
                trimmed(r.originCity()), trimmed(r.originPostalCode()), trimmed(r.originStreet()),
                trimmed(r.destinationCountry()), trimmed(r.destinationCity()),
                trimmed(r.destinationPostalCode()), trimmed(r.destinationStreet()),
                r.transportMethod(), r.status(), r.shippingDate(), r.expectedArrivalDate(),
                r.arrivalDate(), r.offerPriceEur(), r.costEur(), r.volumeM3(), r.grossWeightKg(),
                r.palletCount(), blankToNull(r.notes()), excelPath, excelOriginalFilename,
                movedToDeliveredOn, createdAt, updatedAt);
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private DeliveryDetailDto toDetail(Delivery delivery) {
        long id = delivery.id();
        List<DeliveryItem> itemRows = items.findByDeliveryId(id);
        List<DeliveryPdf> pdfRows = pdfs.findByDeliveryId(id);

        boolean hasExcel = delivery.excelPath() != null;
        Totals sums = hasExcel ? TotalsReconciler.itemSums(itemRows) : null;
        TotalsEdited edited = TotalsReconciler.totalsEdited(delivery, sums, itemRows);

        return new DeliveryDetailDto(
                id,
                delivery.customerCompany(),
                delivery.originCountry(),
                delivery.originCity(),
                delivery.originPostalCode(),
                delivery.originStreet(),
                delivery.destinationCountry(),
                delivery.destinationCity(),
                delivery.destinationPostalCode(),
                delivery.destinationStreet(),
                delivery.transportMethod(),
                delivery.status(),
                delivery.shippingDate(),
                delivery.expectedArrivalDate(),
                delivery.arrivalDate(),
                delivery.offerPriceEur(),
                delivery.costEur(),
                delivery.volumeM3(),
                delivery.grossWeightKg(),
                delivery.palletCount(),
                delivery.notes(),
                delivery.notes() != null && !delivery.notes().isBlank(),
                delivery.excelOriginalFilename(),
                delivery.movedToDeliveredOn(),
                edited,
                sums,
                itemRows.stream().map(ItemDto::from).toList(),
                pdfRows.stream().map(PdfDto::from).toList());
    }
}
