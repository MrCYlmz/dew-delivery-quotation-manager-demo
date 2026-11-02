package de.dew.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class PackingListWorkbooks {

    static final int DESCRIPTION = 0;
    static final int ARTICLE = 1;
    static final int CUSTOMER_CODE = 2;
    static final int PACKAGES = 4;
    static final int PALLETS = 5;
    static final int NET_WEIGHT = 8;
    static final int GROSS_WEIGHT = 10;
    static final int LENGTH = 11;
    static final int WIDTH = 12;
    static final int HEIGHT = 13;
    static final int VOLUME = 14;
    static final int CARTON_FROM = 15;

    static final int FIRST_DATA_ROW = 3;

    private PackingListWorkbooks() {
    }

    static Sheet packingListSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row labels = sheet.createRow(0);
        text(labels, DESCRIPTION, "Description");
        text(labels, ARTICLE, "Article Code");
        text(labels, CUSTOMER_CODE, "Customer Code");
        text(labels, 3, "QTY/\nPackage");
        text(labels, PACKAGES, "No.of Packages");
        text(labels, 6, "Total QTY");
        text(labels, 7, "N.W./\npackage");
        text(labels, NET_WEIGHT, "Total N.W.");
        text(labels, 9, "G.W./\npackage");
        text(labels, GROSS_WEIGHT, "Total G.W.");
        text(labels, LENGTH, "Measurement/\nPackage");
        text(labels, VOLUME, "Total Volume");
        text(labels, CARTON_FROM, "Carton No.");
        text(labels, 17, "Container no.");

        Row subLabels = sheet.createRow(1);
        text(subLabels, LENGTH, "L");
        text(subLabels, WIDTH, "W");
        text(subLabels, HEIGHT, "H");

        Row units = sheet.createRow(2);
        text(units, PACKAGES, "Package");
        text(units, PALLETS, "Pallet");
        text(units, 6, "PCS");
        text(units, 7, "KGS");
        text(units, NET_WEIGHT, "KGS");
        text(units, 9, "KGS");
        text(units, GROSS_WEIGHT, "KGS");
        text(units, LENGTH, "mm");
        text(units, WIDTH, "mm");
        text(units, HEIGHT, "mm");
        text(units, VOLUME, "CBM");
        text(units, CARTON_FROM, "Fm ");
        text(units, 16, "To");
        return sheet;
    }

    static Sheet invoiceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("INVOICE");
        Row labels = sheet.createRow(0);
        text(labels, 0, "Description");
        text(labels, 1, "Article Code");
        text(labels, 2, "Quantity");
        text(labels, 3, "Unit Price");
        text(labels, 4, "Total Amount");
        Row data = sheet.createRow(1);
        text(data, 0, "Display Table Base");
        text(data, 1, "ART-1001");
        number(data, 2, 2d);
        number(data, 3, 85d);
        number(data, 4, 170d);
        return sheet;
    }

    static Row itemRow(Sheet sheet, int rowIndex, String description, String articleNo,
            Double pallets, Double grossWeight, Double netWeight,
            Double length, Double width, Double height, Double volume) {
        Row row = sheet.createRow(rowIndex);
        if (description != null) {
            text(row, DESCRIPTION, description);
        }
        if (articleNo != null) {
            text(row, ARTICLE, articleNo);
        }
        number(row, PALLETS, pallets);
        number(row, GROSS_WEIGHT, grossWeight);
        number(row, NET_WEIGHT, netWeight);
        number(row, LENGTH, length);
        number(row, WIDTH, width);
        number(row, HEIGHT, height);
        number(row, VOLUME, volume);
        return row;
    }

    static Row sectionRow(Sheet sheet, int rowIndex, String label) {
        Row row = sheet.createRow(rowIndex);
        text(row, ARTICLE, label);
        return row;
    }

    static void text(Row row, int column, String value) {
        row.createCell(column).setCellValue(value);
    }

    static void number(Row row, int column, Double value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    static Workbook newWorkbook() {
        return new XSSFWorkbook();
    }

    static InputStream bytes(Workbook workbook) {
        try (Workbook closing = workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            closing.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
