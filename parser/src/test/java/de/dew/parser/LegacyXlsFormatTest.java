package de.dew.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyXlsFormatTest {

    private static final Path XLSX = Path.of("../samples/QX20260115-packing-list.xlsx");
    private static final Path XLS = Path.of("../samples/QX20260115-packing-list.xls");

    @Test
    @DisplayName("§12 via HSSF: the .xls fixture yields the same 20 / 6 / 7.42 / 1580")
    void legacyXlsMatchesAcceptanceTest() throws Exception {
        assertAcceptanceFigures(parse(XLS, "QX20260115-packing-list.xls"));
    }

    @Test
    @DisplayName("§10.1: a .xlsx renamed .xls parses anyway — the container is sniffed, not the name")
    void misleadingExtensionIsHandled() throws Exception {
        Path liar = Files.createTempFile("renamed", ".xls");
        Files.copy(XLSX, liar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            assertAcceptanceFigures(parse(liar, "renamed.xls"));
        } finally {
            Files.deleteIfExists(liar);
        }
    }

    @Test
    @DisplayName("Both formats agree field for field, not just on the totals")
    void bothFormatsAgreeOnEveryItem() throws Exception {
        PackingListResult fromXlsx = parse(XLSX, "a.xlsx");
        PackingListResult fromXls = parse(XLS, "a.xls");

        assertEquals(fromXlsx.items().size(), fromXls.items().size());
        for (int i = 0; i < fromXlsx.items().size(); i++) {
            PackingListItem expected = fromXlsx.items().get(i);
            PackingListItem actual = fromXls.items().get(i);
            assertEquals(expected.sourceRow(), actual.sourceRow(), "sourceRow at index " + i);
            assertEquals(expected.description(), actual.description(), "description at row " + expected.sourceRow());
            assertEquals(expected.volumeMismatch(), actual.volumeMismatch(), "mismatch at row " + expected.sourceRow());
            assertEqualsNullable(expected.netWeightKg(), actual.netWeightKg(), "netWeight at row " + expected.sourceRow());
            assertEqualsNullable(expected.volumeM3(), actual.volumeM3(), "volume at row " + expected.sourceRow());
            assertEqualsNullable(expected.palletCount(), actual.palletCount(), "pallets at row " + expected.sourceRow());
        }
    }

    private static void assertAcceptanceFigures(PackingListResult r) {
        assertEquals(20, r.items().size(), "item count");
        assertEquals(0, new BigDecimal("6").compareTo(r.totalPalletCount()), "pallets: " + r.totalPalletCount());
        assertEquals(0, new BigDecimal("7.42").compareTo(r.totalVolumeM3()), "volume: " + r.totalVolumeM3());
        assertEquals(0, new BigDecimal("1580").compareTo(r.totalGrossWeightKg()), "gross: " + r.totalGrossWeightKg());
        assertEquals(0, r.volumeMismatchCount(), "volume mismatches");

        BigDecimal net = BigDecimal.ZERO;
        for (PackingListItem item : r.items()) {
            if (item.netWeightKg() != null) {
                net = net.add(item.netWeightKg());
            }
        }
        assertEquals(0, new BigDecimal("1264.8").compareTo(net), "net summed over items: " + net);
    }

    private static void assertEqualsNullable(BigDecimal expected, BigDecimal actual, String message) {
        if (expected == null || actual == null) {
            assertEquals(expected == null, actual == null, message);
            return;
        }
        assertEquals(0, expected.compareTo(actual), message + " (" + expected + " vs " + actual + ")");
    }

    private static PackingListResult parse(Path path, String filename) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return new DefaultPackingListParser().parse(in, filename);
        }
    }
}
