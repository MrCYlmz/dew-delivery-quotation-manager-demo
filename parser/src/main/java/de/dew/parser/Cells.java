package de.dew.parser;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

final class Cells {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2007\\u202f\\u205f\\u3000\\ufeff]+");

    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile("^([+-]?[0-9][0-9.,\\u00a0 ']*)([^0-9]*)$");

    private Cells() {
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return WHITESPACE.matcher(raw).replaceAll(" ").trim().toLowerCase();
    }

    static String normalized(Cell cell) {
        return normalize(text(cell));
    }

    static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        String value = switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> null;
        };
        return value == null || value.isBlank() ? null : value;
    }

    static BigDecimal number(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        if (type != CellType.STRING) {
            return null;
        }
        return parseNumber(cell.getStringCellValue());
    }

    static BigDecimal parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = WHITESPACE.matcher(raw).replaceAll("").replace("'", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        Matcher matcher = NUMBER_WITH_UNIT.matcher(cleaned);
        if (!matcher.matches()) {
            return null;
        }
        String digits = matcher.group(1);
        try {
            return new BigDecimal(decimalPoint(digits));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String decimalPoint(String digits) {
        int lastComma = digits.lastIndexOf(',');
        int lastDot = digits.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            char decimal = lastComma > lastDot ? ',' : '.';
            char grouping = decimal == ',' ? '.' : ',';
            return digits.replace(String.valueOf(grouping), "").replace(decimal, '.');
        }
        if (lastComma >= 0) {
            boolean grouping = digits.indexOf(',') != lastComma
                    || (digits.length() - lastComma - 1 == 3 && lastComma > 0);
            return grouping ? digits.replace(",", "") : digits.replace(',', '.');
        }
        if (lastDot >= 0 && digits.indexOf('.') != lastDot) {
            return digits.replace(".", "");
        }
        return digits;
    }

    static boolean hasNumberIn(Row row, int firstColumn, int lastColumn) {
        for (int column = firstColumn; column <= lastColumn; column++) {
            if (number(row.getCell(column)) != null) {
                return true;
            }
        }
        return false;
    }

    static int lastColumn(Row row) {
        return row == null ? -1 : row.getLastCellNum() - 1;
    }
}
