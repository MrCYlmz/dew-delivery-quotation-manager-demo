package de.dew.parser;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPackingListParser implements PackingListParser {

    private static final Logger log = LoggerFactory.getLogger(DefaultPackingListParser.class);

    private static final String SHEET_NAME_NEEDLE = "packing list";

    private static final List<String> TOTAL_MARKERS = List.of("total", "sum", "summe", "gesamt", "合计", "总计");

    private static final int MARKER_FIRST_COLUMN = 0;
    private static final int MARKER_LAST_COLUMN = 2;

    private static final int BLOCK_FIRST_COLUMN = 3;
    private static final int BLOCK_LAST_COLUMN = 14;

    private static final BigDecimal VOLUME_TOLERANCE_M3 = new BigDecimal("0.01");

    private static final int MM3_TO_M3_DECIMAL_SHIFT = 9;

    @Override
    public PackingListResult parse(InputStream in, String originalFilename) throws PackingListParseException {
        String source = originalFilename == null || originalFilename.isBlank() ? "the uploaded file" : originalFilename;
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(in);
        } catch (IOException | RuntimeException e) {
            throw new PackingListParseException(
                    "'%s' could not be opened as an Excel workbook (%s). Expected .xlsx or .xls."
                            .formatted(source, e.getClass().getSimpleName()),
                    e);
        }
        try (workbook) {
            Sheet sheet = selectSheet(workbook, source);
            HeaderLayout layout = HeaderLayout.of(sheet, source);
            log.debug("Parsing '{}' sheet '{}': header row {}, units row {}, data from row {}; {}",
                    source, sheet.getSheetName(), layout.labelRow() + 1, layout.unitRow() + 1,
                    layout.firstDataRow() + 1, layout.describe());
            return read(sheet, layout);
        } catch (IOException e) {
            throw new PackingListParseException("'%s' could not be read to the end.".formatted(source), e);
        }
    }

    private Sheet selectSheet(Workbook workbook, String source) throws PackingListParseException {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (Cells.normalize(sheet.getSheetName()).contains(SHEET_NAME_NEEDLE)) {
                log.debug("'{}': selected sheet '{}' by name (8.1 rule 1)", source, sheet.getSheetName());
                return sheet;
            }
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (HeaderLayout.hasSignature(sheet)) {
                log.info("'{}': no sheet named 'packing list'; selected '{}' by header signature (8.1 rule 2)",
                        source, sheet.getSheetName());
                return sheet;
            }
        }
        List<String> names = IntStream.range(0, workbook.getNumberOfSheets())
                .mapToObj(i -> "'" + workbook.getSheetName(i) + "'")
                .toList();
        throw new PackingListParseException(("No packing list sheet found in '%s'. Looked for a sheet named "
                + "'packing list' and then for one whose header carries both 'Total Volume' and 'Carton No.'; "
                + "neither matched any of its %d sheet(s): %s. The first sheet is deliberately not used as a "
                + "fallback — in an untouched factory file that is the invoice.")
                        .formatted(source, names.size(), String.join(", ", names)));
    }

    private PackingListResult read(Sheet sheet, HeaderLayout layout) {
        List<PackingListItem> items = new ArrayList<>();
        BigDecimal totalPallets = BigDecimal.ZERO;
        BigDecimal totalGrossWeight = BigDecimal.ZERO;
        BigDecimal totalVolume = BigDecimal.ZERO;
        int mismatches = 0;
        int skipped = 0;
        String carriedDescription = null;

        for (int r = layout.firstDataRow(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            if (isTotalRow(row)) {
                log.debug("Sheet '{}': stopping before total row {}", sheet.getSheetName(), r + 1);
                break;
            }
            if (!Cells.hasNumberIn(row, BLOCK_FIRST_COLUMN, BLOCK_LAST_COLUMN)) {
                skipped++;
                continue;
            }

            String description = Cells.text(row.getCell(layout.descriptionColumn()));
            if (description != null) {
                carriedDescription = description.trim();
            }

            BigDecimal pallets = Cells.number(row.getCell(layout.palletColumn()));
            BigDecimal grossWeight = Cells.number(row.getCell(layout.grossWeightColumn()));
            BigDecimal netWeight = Cells.number(row.getCell(layout.netWeightColumn()));
            BigDecimal volume = Cells.number(row.getCell(layout.volumeColumn()));
            BigDecimal length = Cells.number(row.getCell(layout.lengthColumn()));
            BigDecimal width = Cells.number(row.getCell(layout.widthColumn()));
            BigDecimal height = Cells.number(row.getCell(layout.heightColumn()));
            String articleNo = layout.articleColumn() < 0 ? null : Cells.text(row.getCell(layout.articleColumn()));

            totalPallets = add(totalPallets, pallets);
            totalGrossWeight = add(totalGrossWeight, grossWeight);
            totalVolume = add(totalVolume, volume);

            boolean volumeMismatch = contradictsStatedVolume(length, width, height, pallets, volume);
            if (volumeMismatch) {
                mismatches++;
                log.info("Row {} of sheet '{}': stated volume {} m3 disagrees with {}x{}x{} mm x {} pallet(s) by more "
                        + "than {} m3", r + 1, sheet.getSheetName(), volume, length, width, height, pallets,
                        VOLUME_TOLERANCE_M3);
            }

            items.add(new PackingListItem(r + 1, carriedDescription, articleNo, length, width, height,
                    pallets, volume, grossWeight, netWeight, volumeMismatch));
        }

        log.info("Sheet '{}': {} item rows kept, {} section rows skipped, {} volume mismatch(es); "
                + "totals {} m3 / {} kg gross / {} pallets",
                sheet.getSheetName(), items.size(), skipped, mismatches, totalVolume, totalGrossWeight, totalPallets);
        if (items.isEmpty()) {
            log.warn("Sheet '{}' has a valid packing list header but no item rows.", sheet.getSheetName());
        }
        return new PackingListResult(totalVolume, totalGrossWeight, totalPallets, List.copyOf(items),
                mismatches, sheet.getSheetName());
    }

    private static boolean isTotalRow(Row row) {
        for (int c = MARKER_FIRST_COLUMN; c <= MARKER_LAST_COLUMN; c++) {
            String value = Cells.normalized(row.getCell(c));
            if (value.isEmpty()) {
                continue;
            }
            return TOTAL_MARKERS.stream().anyMatch(value::startsWith);
        }
        return false;
    }

    private static boolean contradictsStatedVolume(BigDecimal length, BigDecimal width, BigDecimal height,
            BigDecimal pallets, BigDecimal statedVolume) {
        if (length == null || width == null || height == null || pallets == null || statedVolume == null) {
            return false;
        }
        BigDecimal calculated = length.multiply(width).multiply(height)
                .movePointLeft(MM3_TO_M3_DECIMAL_SHIFT)
                .multiply(pallets);
        return calculated.subtract(statedVolume).abs().compareTo(VOLUME_TOLERANCE_M3) > 0;
    }

    private static BigDecimal add(BigDecimal running, BigDecimal value) {
        return value == null ? running : running.add(value);
    }
}
