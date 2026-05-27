package com.practice.demo.util;

import com.practice.demo.dto.PortfolioEntry;
import com.practice.demo.exception.InvalidFileException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a portfolio Excel file (.xls or .xlsx) using Apache POI.
 *
 * Expected sheet layout (first sheet, row 0 = header):
 * ┌───────────────┬──────────┬───────────────┐
 * │  Stock Name   │ Quantity │ Buying Price  │
 * ├───────────────┼──────────┼───────────────┤
 * │  RELIANCE     │    10    │   2800.00     │
 * │  TCS          │     5    │   3500.50     │
 * └───────────────┴──────────┴───────────────┘
 *
 * Column indices: 0 = Stock Name, 1 = Quantity, 2 = Buying Price.
 * The "Stock Name" value is returned as-is; symbol normalisation and
 * Nifty 50 validation are performed by {@link com.practice.demo.service.PortfolioService}.
 */
@Component
public class ExcelParser {

    private static final Logger logger = LoggerFactory.getLogger(ExcelParser.class);

    private static final int    COL_STOCK_NAME   = 0;
    private static final int    COL_QUANTITY      = 1;
    private static final int    COL_BUYING_PRICE  = 2;
    private static final long   MAX_FILE_BYTES    = 5L * 1024 * 1024; // 5 MB

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parses the uploaded file and returns one {@link PortfolioEntry} per
     * valid data row.  Rows with unreadable data are skipped; the reason
     * is added to the {@code parseErrors} list inside the returned wrapper.
     *
     * @param file the uploaded .xls / .xlsx file
     * @return wrapper containing parsed entries and any per-row errors
     */
    public ParseResult parse(MultipartFile file) {
        validateFile(file);

        List<PortfolioEntry> entries    = new ArrayList<>();
        List<String>         parseErrors = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            logger.info("Parsing '{}' — {} row(s) detected (including header)",
                    file.getOriginalFilename(), sheet.getLastRowNum() + 1);

            // Row 0 is the header — start from row 1
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (isRowEmpty(row)) continue;

                try {
                    PortfolioEntry entry = parseRow(row, workbook);
                    entries.add(entry);
                    logger.debug("Row {}: parsed '{}' qty={} price={}",
                            rowIdx + 1, entry.getSymbol(), entry.getQuantity(), entry.getBuyingPrice());
                } catch (Exception ex) {
                    String error = "Row " + (rowIdx + 1) + ": " + ex.getMessage();
                    parseErrors.add(error);
                    logger.warn("Skipping row {} — {}", rowIdx + 1, ex.getMessage());
                }
            }

        } catch (IOException ex) {
            throw new InvalidFileException("Cannot read the Excel file: " + ex.getMessage());
        }

        logger.info("Excel parse complete: {} valid row(s), {} error(s)", entries.size(), parseErrors.size());
        return new ParseResult(entries, parseErrors);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file must not be empty");
        }
        String name = file.getOriginalFilename();
        if (name == null || (!name.toLowerCase().endsWith(".xls") && !name.toLowerCase().endsWith(".xlsx"))) {
            throw new InvalidFileException("Only .xls and .xlsx files are accepted");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidFileException("File size must not exceed 5 MB");
        }
    }

    private PortfolioEntry parseRow(Row row, Workbook workbook) {
        String stockName = readString(row.getCell(COL_STOCK_NAME));
        if (stockName == null || stockName.isBlank()) {
            throw new IllegalArgumentException("Stock name is missing");
        }

        double rawQty   = readNumeric(row.getCell(COL_QUANTITY), workbook);
        double rawPrice = readNumeric(row.getCell(COL_BUYING_PRICE), workbook);

        int quantity = (int) rawQty;
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be a positive integer for '" + stockName + "'");
        }

        BigDecimal buyingPrice = BigDecimal.valueOf(rawPrice).setScale(2, RoundingMode.HALF_UP);
        if (buyingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Buying price must be positive for '" + stockName + "'");
        }

        // Symbol is raw here — PortfolioService will normalise and validate it
        return PortfolioEntry.builder()
                .symbol(stockName.trim())
                .quantity(quantity)
                .buyingPrice(buyingPrice)
                .build();
    }

    /** Reads a cell as a trimmed String regardless of its storage type. */
    private String readString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> null;
        };
    }

    /** Reads a cell as a double (handles STRING, NUMERIC, and FORMULA cells). */
    private double readNumeric(Cell cell, Workbook workbook) {
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING  -> {
                try { yield Double.parseDouble(cell.getStringCellValue().trim()); }
                catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Expected a number but got '" + cell.getStringCellValue() + "'");
                }
            }
            case FORMULA -> workbook.getCreationHelper()
                    .createFormulaEvaluator()
                    .evaluate(cell)
                    .getNumberValue();
            default -> 0;
        };
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = COL_STOCK_NAME; c <= COL_BUYING_PRICE; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Result wrapper
    // -----------------------------------------------------------------------

    /** Carries both the successfully parsed entries and any per-row errors. */
    public record ParseResult(List<PortfolioEntry> entries, List<String> parseErrors) {}
}
