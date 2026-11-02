package de.dew.parser;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;

final class HeaderLayout {

    private static final int HEADER_SCAN_ROWS = 12;

    private static final int UNIT_ROW_LOOKAHEAD = 3;

    private static final int MAX_COLUMN_SCAN = 64;

    private final int labelRow;
    private final int unitRow;
    private final int descriptionColumn;
    private final int articleColumn;
    private final int palletColumn;
    private final int grossWeightColumn;
    private final int netWeightColumn;
    private final int volumeColumn;
    private final int lengthColumn;
    private final int widthColumn;
    private final int heightColumn;

    private HeaderLayout(int labelRow, int unitRow, int descriptionColumn, int articleColumn,
            int palletColumn, int grossWeightColumn, int netWeightColumn, int volumeColumn,
            int lengthColumn, int widthColumn, int heightColumn) {
        this.labelRow = labelRow;
        this.unitRow = unitRow;
        this.descriptionColumn = descriptionColumn;
        this.articleColumn = articleColumn;
        this.palletColumn = palletColumn;
        this.grossWeightColumn = grossWeightColumn;
        this.netWeightColumn = netWeightColumn;
        this.volumeColumn = volumeColumn;
        this.lengthColumn = lengthColumn;
        this.widthColumn = widthColumn;
        this.heightColumn = heightColumn;
    }

    static boolean hasSignature(Sheet sheet) {
        return findLabelRow(sheet) >= 0;
    }

    static HeaderLayout of(Sheet sheet, String originalFilename) throws PackingListParseException {
        int labelRow = findLabelRow(sheet);
        if (labelRow < 0) {
            throw new PackingListParseException(("Sheet '%s' in '%s' does not look like a packing list: "
                    + "no header row carrying both 'Total Volume' and 'Carton No.' in its first %d rows.")
                            .formatted(sheet.getSheetName(), originalFilename, HEADER_SCAN_ROWS));
        }
        int unitRow = findUnitRow(sheet, labelRow);
        if (unitRow < 0) {
            throw new PackingListParseException(("Sheet '%s' in '%s' has a packing list header on row %d but no unit "
                    + "row beneath it: expected a row within %d rows below carrying both 'CBM' and 'Pallet'. "
                    + "The pallet column can only be identified from that row — columns E and F share the single "
                    + "merged label 'No.of Packages'.")
                            .formatted(sheet.getSheetName(), originalFilename, labelRow + 1, UNIT_ROW_LOOKAHEAD));
        }

        Row labels = sheet.getRow(labelRow);
        Row units = sheet.getRow(unitRow);

        int description = columnContaining(labels, "description");
        int netWeight = columnContaining(labels, "total n.w");
        int grossWeight = columnContaining(labels, "total g.w");
        int volume = columnContaining(labels, "total volume");
        if (volume < 0) {
            volume = columnContaining(units, "cbm");
        }
        int pallet = columnContaining(units, "pallet");

        int[] dimensions = findDimensionColumns(sheet, labelRow, unitRow);

        List<String> missing = new ArrayList<>();
        if (description < 0) {
            missing.add("Description");
        }
        if (pallet < 0) {
            missing.add("No.of Packages / Pallet (the 'Pallet' unit, not the merged row-1 label)");
        }
        if (grossWeight < 0) {
            missing.add("Total G.W.");
        }
        if (netWeight < 0) {
            missing.add("Total N.W.");
        }
        if (volume < 0) {
            missing.add("Total Volume");
        }
        if (dimensions == null) {
            missing.add("Measurement/Package L, W and H");
        }
        if (!missing.isEmpty()) {
            throw new PackingListParseException(("Sheet '%s' in '%s' is missing %d required packing list column(s): %s. "
                    + "Header found on row %d with units on row %d. The columns were looked up by label, not by "
                    + "position, so a renamed or removed header is the likely cause.")
                            .formatted(sheet.getSheetName(), originalFilename, missing.size(),
                                    String.join("; ", missing), labelRow + 1, unitRow + 1));
        }

        int article = columnContaining(labels, "article");
        if (article < 0 && description + 1 <= Cells.lastColumn(labels)) {
            article = description + 1;
        }

        return new HeaderLayout(labelRow, unitRow, description, article, pallet, grossWeight, netWeight,
                volume, dimensions[0], dimensions[1], dimensions[2]);
    }

    private static int findLabelRow(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS - 1);
        for (int r = sheet.getFirstRowNum(); r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row != null && columnContaining(row, "total volume") >= 0 && columnContaining(row, "carton no") >= 0) {
                return r;
            }
        }
        return -1;
    }

    private static int findUnitRow(Sheet sheet, int labelRow) {
        for (int r = labelRow + 1; r <= Math.min(labelRow + UNIT_ROW_LOOKAHEAD, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row != null && columnContaining(row, "cbm") >= 0 && columnContaining(row, "pallet") >= 0) {
                return r;
            }
        }
        return -1;
    }

    private static int[] findDimensionColumns(Sheet sheet, int labelRow, int unitRow) {
        for (int r = labelRow + 1; r <= unitRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int length = columnEqualTo(row, "l");
            int width = columnEqualTo(row, "w");
            int height = columnEqualTo(row, "h");
            if (length >= 0 && width >= 0 && height >= 0 && length != width && width != height && length != height) {
                return new int[] { length, width, height };
            }
        }
        Row units = sheet.getRow(unitRow);
        List<Integer> millimetres = new ArrayList<>();
        int last = Math.min(Cells.lastColumn(units), MAX_COLUMN_SCAN);
        for (int c = 0; c <= last; c++) {
            if (Cells.normalized(units.getCell(c)).equals("mm")) {
                millimetres.add(c);
            }
        }
        if (millimetres.size() == 3) {
            return new int[] { millimetres.get(0), millimetres.get(1), millimetres.get(2) };
        }
        return null;
    }

    private static int columnContaining(Row row, String needle) {
        if (row == null) {
            return -1;
        }
        int last = Math.min(Cells.lastColumn(row), MAX_COLUMN_SCAN);
        for (int c = 0; c <= last; c++) {
            if (Cells.normalized(row.getCell(c)).contains(needle)) {
                return c;
            }
        }
        return -1;
    }

    private static int columnEqualTo(Row row, String label) {
        if (row == null) {
            return -1;
        }
        int last = Math.min(Cells.lastColumn(row), MAX_COLUMN_SCAN);
        for (int c = 0; c <= last; c++) {
            if (Cells.normalized(row.getCell(c)).equals(label)) {
                return c;
            }
        }
        return -1;
    }

    int firstDataRow() {
        return unitRow + 1;
    }

    int labelRow() {
        return labelRow;
    }

    int unitRow() {
        return unitRow;
    }

    int descriptionColumn() {
        return descriptionColumn;
    }

    int articleColumn() {
        return articleColumn;
    }

    int palletColumn() {
        return palletColumn;
    }

    int grossWeightColumn() {
        return grossWeightColumn;
    }

    int netWeightColumn() {
        return netWeightColumn;
    }

    int volumeColumn() {
        return volumeColumn;
    }

    int lengthColumn() {
        return lengthColumn;
    }

    int widthColumn() {
        return widthColumn;
    }

    int heightColumn() {
        return heightColumn;
    }

    String describe() {
        return "pallets=%s grossWeight=%s netWeight=%s volume=%s L/W/H=%s/%s/%s description=%s article=%s".formatted(
                name(palletColumn), name(grossWeightColumn), name(netWeightColumn), name(volumeColumn),
                name(lengthColumn), name(widthColumn), name(heightColumn), name(descriptionColumn),
                name(articleColumn));
    }

    private static String name(int column) {
        return column < 0 ? "-" : CellReference.convertNumToColString(column);
    }
}
