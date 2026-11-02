package de.dew.backend;

import de.dew.parser.PackingListItem;
import de.dew.parser.PackingListParseException;
import de.dew.parser.PackingListParser;
import de.dew.parser.PackingListResult;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

public class FakePackingListParser implements PackingListParser {

    private PackingListResult result;
    private PackingListParseException failure;
    private int calls;
    private String lastFilename;
    private byte[] lastContent;

    public void willReturn(PackingListResult next) {
        this.result = next;
        this.failure = null;
    }

    public void willFail(String message) {
        this.failure = new PackingListParseException(message);
        this.result = null;
    }

    public void reset() {
        result = null;
        failure = null;
        calls = 0;
        lastFilename = null;
        lastContent = null;
    }

    public int calls() {
        return calls;
    }

    public String lastFilename() {
        return lastFilename;
    }

    public byte[] lastContent() {
        return lastContent;
    }

    @Override
    public PackingListResult parse(InputStream in, String originalFilename)
            throws PackingListParseException {
        calls++;
        lastFilename = originalFilename;
        try {
            lastContent = in.readAllBytes();
        } catch (IOException e) {
            throw new PackingListParseException("Could not read the stream", e);
        }
        if (failure != null) {
            throw failure;
        }
        if (result == null) {
            throw new IllegalStateException("FakePackingListParser was not told what to return");
        }
        return result;
    }

    public static PackingListResult sampleResult() {
        List<PackingListItem> items = List.of(
                new PackingListItem(5, "Chair", "A-100", bd("1200"), bd("800"), bd("1050"),
                        bd("2"), bd("2.02"), bd("480"), bd("395.5"), false),
                new PackingListItem(6, "Chair", "A-101", null, null, null, null, null, null,
                        bd("120.25"), false),
                new PackingListItem(7, "Table", "B-200", bd("1100"), bd("700"), bd("900"),
                        bd("4"), bd("5.40"), bd("1100"), bd("2019.35"), true));
        return new PackingListResult(bd("7.42"), bd("1580"), bd("6"), items, 1, "PACKING LIST ");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
