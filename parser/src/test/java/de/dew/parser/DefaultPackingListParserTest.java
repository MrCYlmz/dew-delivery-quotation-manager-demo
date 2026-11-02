package de.dew.parser;

import static de.dew.parser.PackingListWorkbooks.ARTICLE;
import static de.dew.parser.PackingListWorkbooks.CUSTOMER_CODE;
import static de.dew.parser.PackingListWorkbooks.FIRST_DATA_ROW;
import static de.dew.parser.PackingListWorkbooks.GROSS_WEIGHT;
import static de.dew.parser.PackingListWorkbooks.PALLETS;
import static de.dew.parser.PackingListWorkbooks.VOLUME;
import static de.dew.parser.PackingListWorkbooks.bytes;
import static de.dew.parser.PackingListWorkbooks.invoiceSheet;
import static de.dew.parser.PackingListWorkbooks.itemRow;
import static de.dew.parser.PackingListWorkbooks.newWorkbook;
import static de.dew.parser.PackingListWorkbooks.packingListSheet;
import static de.dew.parser.PackingListWorkbooks.sectionRow;
import static de.dew.parser.PackingListWorkbooks.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultPackingListParserTest {

    private final PackingListParser parser = new DefaultPackingListParser();

    @Nested
    @DisplayName("SPEC.md section 12 — the committed fixture")
    class Fixture {

        @Test
        @DisplayName("20 items, 6 pallets, 7.42 m3, 1580 kg gross — without reading the TOTAL row")
        void reproducesTheSheetsOwnTotals() throws Exception {
            PackingListResult result = parseFixture();

            assertAll(
                    () -> assertEquals(20, result.items().size(), "item rows kept"),
                    () -> assertEqualValue("6", result.totalPalletCount(), "pallets"),
                    () -> assertEqualValue("7.42", result.totalVolumeM3(), "volume m3"),
                    () -> assertEqualValue("1580", result.totalGrossWeightKg(), "gross weight kg"),
                    () -> assertEqualValue("1264.8", sumNetWeight(result), "net weight kg"),
                    () -> assertFalse(sourceRows(result).contains(27),
                            "row 27 is the TOTAL row of live SUM() formulas and must be excluded (8.4)"));
        }

        @Test
        @DisplayName("section rows 4, 20 and 25 are skipped; every other row 5-26 is kept")
        void skipsTheThreeSectionRows() throws Exception {
            List<Integer> rows = sourceRows(parseFixture());

            List<Integer> expected = IntStream.rangeClosed(5, 26).boxed()
                    .filter(r -> r != 20 && r != 25)
                    .toList();
            assertAll(
                    () -> assertEquals(expected, rows, "kept source rows"),
                    () -> assertFalse(rows.contains(4), "row 4 is QX20260115 / Example Store Fitters"),
                    () -> assertFalse(rows.contains(20), "row 20 is Hardgoods"),
                    () -> assertFalse(rows.contains(25), "row 25 is Example Store Fitters"));
        }

        @Test
        @DisplayName("rows carrying only a net weight are still kept")
        void keepsRowsWithoutSummedFigures() throws Exception {
            List<PackingListItem> items = parseFixture().items();

            long withSummedFigure = items.stream()
                    .filter(i -> i.palletCount() != null || i.grossWeightKg() != null || i.volumeM3() != null)
                    .count();
            assertAll(
                    () -> assertEquals(5, withSummedFigure, "rows carrying a pallet, gross weight or volume"),
                    () -> assertEquals(20, items.size(), "all rows kept regardless"));
        }

        @Test
        @DisplayName("a blank description is carried forward from the row above")
        void carriesDescriptionForward() throws Exception {
            List<PackingListItem> items = parseFixture().items();

            assertAll(
                    () -> assertEquals("Display Table Base", item(items, 5).description()),
                    () -> assertEquals("Display Table Base", item(items, 6).description(), "A5:A6 is merged"),
                    () -> assertEquals("Display Cube 400", item(items, 16).description()),
                    () -> assertEquals("Display Cube 400", item(items, 17).description(), "A16:A17 is merged"),
                    () -> assertEquals("Gondola Upright H1800", item(items, 24).description(),
                            "A22:A24 is merged"));
        }

        @Test
        @DisplayName("blank cells stay null on the item rather than becoming zero")
        void blankCellsStayNull() throws Exception {
            List<PackingListItem> items = parseFixture().items();

            PackingListItem netWeightOnly = item(items, 10);
            PackingListItem full = item(items, 5);
            assertAll(
                    () -> assertNull(netWeightOnly.palletCount(), "pallet count"),
                    () -> assertNull(netWeightOnly.grossWeightKg(), "gross weight"),
                    () -> assertNull(netWeightOnly.volumeM3(), "volume"),
                    () -> assertNull(netWeightOnly.lengthMm(), "length"),
                    () -> assertEqualValue("16.5", netWeightOnly.netWeightKg(), "net weight"),
                    () -> assertEqualValue("1200", full.lengthMm(), "length of row 5"),
                    () -> assertEqualValue("800", full.widthMm(), "width of row 5"),
                    () -> assertEqualValue("1000", full.heightMm(), "height of row 5"),
                    () -> assertEquals("ART-1001", full.articleNo(), "article no of row 5"));
        }

        @Test
        @DisplayName("no row is flagged by the volume cross-check")
        void noVolumeMismatches() throws Exception {
            PackingListResult result = parseFixture();

            assertAll(
                    () -> assertEquals(0, result.volumeMismatchCount(), "mismatch count"),
                    () -> assertTrue(result.items().stream().noneMatch(PackingListItem::volumeMismatch),
                            "no item flagged"));
        }

        @Test
        @DisplayName("the sheet is matched by name despite its trailing space")
        void reportsTheSheetName() throws Exception {
            assertEquals("PACKING LIST ", parseFixture().sheetName());
        }
    }

    @Nested
    @DisplayName("SPEC.md 8.1 — sheet selection")
    class SheetSelection {

        @Test
        @DisplayName("the packing list is selected even though the invoice sheet comes first")
        void skipsTheLeadingInvoiceSheet() throws Exception {
            Workbook workbook = newWorkbook();
            invoiceSheet(workbook);
            Sheet packingList = packingListSheet(workbook, "PACKING LIST ");
            itemRow(packingList, FIRST_DATA_ROW, "Display Table Base", "ART-1001", 2d, 420d, 120d,
                    1200d, 800d, 1000d, 1.92d);

            PackingListResult result = parser.parse(bytes(workbook), "QX20260115 INV & PAC.xlsx");

            assertAll(
                    () -> assertEquals("PACKING LIST ", result.sheetName()),
                    () -> assertEquals(1, result.items().size()),
                    () -> assertEqualValue("2", result.totalPalletCount(), "pallets"));
        }

        @Test
        @DisplayName("a packing list on a sheet with an unhelpful name is found by its header")
        void findsThePackingListByHeaderSignature() throws Exception {
            Workbook workbook = newWorkbook();
            invoiceSheet(workbook);
            Sheet packingList = packingListSheet(workbook, "Sheet2");
            itemRow(packingList, FIRST_DATA_ROW, "Display Table Base", "ART-1001", 2d, 420d, 120d,
                    null, null, null, 1.92d);

            PackingListResult result = parser.parse(bytes(workbook), "renamed.xlsx");

            assertEquals("Sheet2", result.sheetName());
        }

        @Test
        @DisplayName("a workbook with no packing list throws instead of falling back to the first sheet")
        void refusesToFallBackToTheInvoice() {
            Workbook workbook = newWorkbook();
            invoiceSheet(workbook);
            InputStream in = bytes(workbook);

            PackingListParseException thrown = assertThrows(PackingListParseException.class,
                    () -> parser.parse(in, "invoice only.xlsx"));

            assertAll(
                    () -> assertTrue(thrown.getMessage().contains("No packing list sheet found"),
                            () -> "message should say what was missing: " + thrown.getMessage()),
                    () -> assertTrue(thrown.getMessage().contains("'INVOICE'"),
                            () -> "message should name the sheets it looked at: " + thrown.getMessage()),
                    () -> assertTrue(thrown.getMessage().contains("invoice only.xlsx"),
                            () -> "message should name the file: " + thrown.getMessage()));
        }

        @Test
        @DisplayName("a file that is not a workbook at all fails loudly")
        void rejectsNonWorkbooks() {
            InputStream in = new ByteArrayInputStream("this is not a workbook".getBytes(StandardCharsets.UTF_8));

            PackingListParseException thrown = assertThrows(PackingListParseException.class,
                    () -> parser.parse(in, "notes.txt"));

            assertTrue(thrown.getMessage().contains("notes.txt"), thrown::getMessage);
        }
    }

    @Nested
    @DisplayName("SPEC.md 8.3 — a required column that cannot be mapped stops the parse")
    class RequiredColumns {

        @Test
        @DisplayName("a missing Total G.W. column is named rather than guessed at by position")
        void namesTheMissingColumn() {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            sheet.getRow(0).getCell(GROSS_WEIGHT).setBlank();
            itemRow(sheet, FIRST_DATA_ROW, "Display Table Base", "ART-1001", 2d, 420d, 120d,
                    null, null, null, 1.92d);
            InputStream in = bytes(workbook);

            PackingListParseException thrown = assertThrows(PackingListParseException.class,
                    () -> parser.parse(in, "damaged header.xlsx"));

            assertAll(
                    () -> assertTrue(thrown.getMessage().contains("Total G.W."), thrown::getMessage),
                    () -> assertTrue(thrown.getMessage().contains("required"), thrown::getMessage));
        }

        @Test
        @DisplayName("losing the unit row is reported as losing the pallet column")
        void namesThePalletColumnWhenTheUnitRowIsGone() {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            sheet.getRow(2).getCell(PALLETS).setBlank();
            itemRow(sheet, FIRST_DATA_ROW, "Display Table Base", "ART-1001", 2d, 420d, 120d,
                    null, null, null, 1.92d);
            InputStream in = bytes(workbook);

            PackingListParseException thrown = assertThrows(PackingListParseException.class,
                    () -> parser.parse(in, "no unit row.xlsx"));

            assertAll(
                    () -> assertTrue(thrown.getMessage().contains("Pallet"), thrown::getMessage),
                    () -> assertTrue(thrown.getMessage().contains("PACKING LIST"), thrown::getMessage));
        }
    }

    @Nested
    @DisplayName("SPEC.md 8.2 — the article column")
    class ArticleColumn {

        @Test
        @DisplayName("a label containing 'article' marks the article column, wherever it sits")
        void findsTheArticleColumnByItsLabel() throws Exception {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            sheet.getRow(0).getCell(ARTICLE).setCellValue("Item Ref");
            sheet.getRow(0).getCell(CUSTOMER_CODE).setCellValue("Article No.");
            var row = itemRow(sheet, FIRST_DATA_ROW, "Display Table Base", "REF-77", 2d, 420d, 120d,
                    null, null, null, 1.92d);
            text(row, CUSTOMER_CODE, "ART-1001");

            PackingListResult result = parser.parse(bytes(workbook), "article label.xlsx");

            assertEquals("ART-1001", result.items().get(0).articleNo());
        }

        @Test
        @DisplayName("without an 'article' label, the column right after Description holds the article")
        void takesTheColumnAfterDescriptionWhenNoLabelSaysArticle() throws Exception {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            sheet.getRow(0).getCell(ARTICLE).setCellValue("Item Ref");
            itemRow(sheet, FIRST_DATA_ROW, "Display Table Base", "ART-1001", 2d, 420d, 120d,
                    null, null, null, 1.92d);

            PackingListResult result = parser.parse(bytes(workbook), "other article label.xlsx");

            assertAll(
                    () -> assertEquals(1, result.items().size()),
                    () -> assertEquals("ART-1001", result.items().get(0).articleNo()),
                    () -> assertEqualValue("2", result.totalPalletCount(), "pallets"),
                    () -> assertEqualValue("1.92", result.totalVolumeM3(), "volume"));
        }
    }

    @Nested
    @DisplayName("SPEC.md 8.7 / F-16 — the 0.01 m3 volume cross-check")
    class VolumeCrossCheck {

        @Test
        @DisplayName("flags above 0.01, not at it and not below it")
        void flagsOnlyAboveTheThreshold() throws Exception {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            itemRow(sheet, FIRST_DATA_ROW, "Just below", "A1", 1d, 10d, 9d, 1000d, 1000d, 1000d, 1.009d);
            itemRow(sheet, FIRST_DATA_ROW + 1, "Exactly at", "A2", 1d, 10d, 9d, 1000d, 1000d, 1000d, 1.01d);
            itemRow(sheet, FIRST_DATA_ROW + 2, "Above", "A3", 1d, 10d, 9d, 1000d, 1000d, 1000d, 1.02d);

            PackingListResult result = parser.parse(bytes(workbook), "threshold.xlsx");

            List<PackingListItem> items = result.items();
            assertAll(
                    () -> assertEquals(3, items.size()),
                    () -> assertFalse(items.get(0).volumeMismatch(), "0.009 off is rounding, not a contradiction"),
                    () -> assertFalse(items.get(1).volumeMismatch(), "0.01 exactly is not 'more than 0.01'"),
                    () -> assertTrue(items.get(2).volumeMismatch(), "0.02 off is a contradiction"),
                    () -> assertEquals(1, result.volumeMismatchCount()));
        }

        @Test
        @DisplayName("a row missing one of the five figures is not flagged")
        void doesNotFlagIncompleteRows() throws Exception {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            itemRow(sheet, FIRST_DATA_ROW, "No pallet count", "A1", null, null, 9d, 1000d, 1000d, 1000d, 5d);
            itemRow(sheet, FIRST_DATA_ROW + 1, "No dimensions", "A2", 1d, null, 9d, null, null, null, 5d);

            PackingListResult result = parser.parse(bytes(workbook), "incomplete.xlsx");

            assertEquals(0, result.volumeMismatchCount());
        }
    }

    @Nested
    @DisplayName("SPEC.md 8.4 / 8.5 — row selection and coercion")
    class RowsAndValues {

        @Test
        @DisplayName("reading stops before a total row and its formulas, in several languages")
        void stopsAtTheTotalRow() throws Exception {
            for (String marker : List.of("TOTAL", "Summe", "Gesamt", "合计")) {
                Workbook workbook = newWorkbook();
                Sheet sheet = packingListSheet(workbook, "PACKING LIST");
                itemRow(sheet, FIRST_DATA_ROW, "Kept", "A1", 2d, 10d, 9d, null, null, null, 1d);
                text(sheet.createRow(FIRST_DATA_ROW + 1), 0, marker);
                sheet.getRow(FIRST_DATA_ROW + 1).createCell(PALLETS).setCellValue(2d);
                sheet.getRow(FIRST_DATA_ROW + 1).createCell(VOLUME).setCellValue(1d);
                itemRow(sheet, FIRST_DATA_ROW + 2, "Below the total", "A2", 5d, 10d, 9d, null, null, null, 1d);

                PackingListResult result = parser.parse(bytes(workbook), marker + ".xlsx");

                assertAll("marker " + marker,
                        () -> assertEquals(1, result.items().size()),
                        () -> assertEqualValue("2", result.totalPalletCount(), "pallets"),
                        () -> assertEqualValue("1", result.totalVolumeM3(), "volume"));
            }
        }

        @Test
        @DisplayName("a section row inside the data is skipped without breaking the carry-forward")
        void skipsSectionRows() throws Exception {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            itemRow(sheet, FIRST_DATA_ROW, "Display Table Base", "ART-1001", 2d, 420d, 120d, null, null, null, 1.92d);
            sectionRow(sheet, FIRST_DATA_ROW + 1, "Hardgoods");
            itemRow(sheet, FIRST_DATA_ROW + 2, null, null, 1d, 380d, 122d, null, null, null, 1.8d);

            PackingListResult result = parser.parse(bytes(workbook), "sections.xlsx");

            assertAll(
                    () -> assertEquals(2, result.items().size()),
                    () -> assertEquals("Display Table Base", result.items().get(1).description()),
                    () -> assertEqualValue("3", result.totalPalletCount(), "pallets"),
                    () -> assertEqualValue("3.72", result.totalVolumeM3(), "volume"));
        }

        @Test
        @DisplayName("figures written as text are coerced rather than dropped")
        void coercesTextFigures() throws Exception {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            var row = itemRow(sheet, FIRST_DATA_ROW, "Text figures", "A1", 1d, null, null, null, null, null, null);
            row.createCell(GROSS_WEIGHT).setCellValue("12 kg");
            row.createCell(VOLUME).setCellValue("1,02");
            row.createCell(PackingListWorkbooks.NET_WEIGHT).setCellValue("2 345,6");

            PackingListResult result = parser.parse(bytes(workbook), "text figures.xlsx");

            PackingListItem item = result.items().get(0);
            assertAll(
                    () -> assertEqualValue("12", item.grossWeightKg(), "'12 kg'"),
                    () -> assertEqualValue("1.02", item.volumeM3(), "'1,02'"),
                    () -> assertEqualValue("2345.6", item.netWeightKg(), "'2 345,6'"),
                    () -> assertEqualValue("12", result.totalGrossWeightKg(), "gross total"),
                    () -> assertEqualValue("1.02", result.totalVolumeM3(), "volume total"));
        }

        @Test
        @DisplayName("a reference like QX20260115 does not turn a section row into an item")
        void doesNotTreatIdentifiersAsFigures() throws Exception {
            Workbook workbook = newWorkbook();
            Sheet sheet = packingListSheet(workbook, "PACKING LIST");
            var reference = sheet.createRow(FIRST_DATA_ROW);
            text(reference, 0, "QX20260115");
            text(reference, 1, "Example Store Fitters");
            reference.createCell(6).setCellValue("CC-0101");
            itemRow(sheet, FIRST_DATA_ROW + 1, "Display Table Base", "ART-1001", 2d, 420d, 120d,
                    null, null, null, 1.92d);

            PackingListResult result = parser.parse(bytes(workbook), "reference row.xlsx");

            assertAll(
                    () -> assertEquals(1, result.items().size()),
                    () -> assertEquals("Display Table Base", result.items().get(0).description()));
        }
    }

    private PackingListResult parseFixture() throws Exception {
        try (InputStream in = Files.newInputStream(fixture())) {
            return parser.parse(in, "QX20260115 INV & PAC.xlsx");
        }
    }

    private static Path fixture() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && directory != null; i++, directory = directory.getParent()) {
            Path candidate = directory.resolve("samples").resolve("QX20260115-packing-list.xlsx");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Could not find samples/QX20260115-packing-list.xlsx above " + Path.of("").toAbsolutePath());
    }

    private static PackingListItem item(List<PackingListItem> items, int sourceRow) {
        PackingListItem found = items.stream().filter(i -> i.sourceRow() == sourceRow).findFirst().orElse(null);
        assertNotNull(found, () -> "no item parsed from source row " + sourceRow);
        return found;
    }

    private static List<Integer> sourceRows(PackingListResult result) {
        return result.items().stream().map(PackingListItem::sourceRow).toList();
    }

    private static BigDecimal sumNetWeight(PackingListResult result) {
        return result.items().stream()
                .map(PackingListItem::netWeightKg)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void assertEqualValue(String expected, BigDecimal actual, String what) {
        assertNotNull(actual, () -> what + " should not be null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "%s: expected %s but was %s".formatted(what, expected, actual));
    }
}
