package com.example.chequebookrequestportal.export;

import com.example.chequebookrequestportal.common.ApiException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class VendorPrintResponseWorkbookParser {
    private static final List<String> HEADERS = List.of("S/N", "Account Number", "Account Name", "Branch Name",
            "Branch Address", "Sort Code", "Cheque Type", "No of Leaves", "No of Booklets", "Delivery SolID",
            "Delivery Address", "Scheme Code", "Currency", "Range Start", "Range Stop");

    public VendorPrintResponseInput parse(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw invalid("Select a non-empty Excel workbook.");
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx"))
            throw invalid("The uploaded workbook must use the .xlsx file extension.");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0)
                throw invalid("The workbook does not contain a worksheet.");
            return parse(workbook.getSheetAt(0), workbook.getCreationHelper().createFormulaEvaluator());
        }
        catch (ApiException exception) {
            throw exception;
        }
        catch (IOException | RuntimeException exception) {
            throw invalid("The uploaded file is not a readable .xlsx workbook.");
        }
    }

    private VendorPrintResponseInput parse(Sheet sheet, FormulaEvaluator evaluator) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        int headerIndex = sheet.getFirstRowNum();
        Row header = sheet.getRow(headerIndex);
        if (header == null)
            throw invalid("The workbook does not contain the required header row.");
        for (int column = 0; column < HEADERS.size(); column++) {
            if (!HEADERS.get(column).equals(value(header, column, formatter, evaluator)))
                throw invalid("The Excel headers must be: " + String.join(", ", HEADERS) + ".");
        }
        for (int column = HEADERS.size(); column < Math.max(header.getLastCellNum(), HEADERS.size()); column++) {
            if (!value(header, column, formatter, evaluator).isBlank())
                throw invalid("The Excel header row contains an unexpected column after Range Stop.");
        }

        List<VendorPrintResponseInput.Row> rows = new ArrayList<>();
        for (int rowIndex = headerIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (blank(row, formatter, evaluator)) continue;
            String leaves = value(row, 7, formatter, evaluator);
            String booklets = value(row, 8, formatter, evaluator);
            rows.add(new VendorPrintResponseInput.Row(
                    value(row, 0, formatter, evaluator), value(row, 1, formatter, evaluator),
                    value(row, 2, formatter, evaluator), value(row, 3, formatter, evaluator),
                    value(row, 4, formatter, evaluator), value(row, 5, formatter, evaluator),
                    value(row, 6, formatter, evaluator), integer(leaves, rowIndex + 1, "No of Leaves"),
                    integer(booklets, rowIndex + 1, "No of Booklets"), value(row, 9, formatter, evaluator),
                    value(row, 10, formatter, evaluator), value(row, 11, formatter, evaluator),
                    value(row, 12, formatter, evaluator), value(row, 13, formatter, evaluator),
                    value(row, 14, formatter, evaluator)));
        }
        if (rows.isEmpty())
            throw invalid("The workbook must contain at least one vendor response row.");
        return new VendorPrintResponseInput(rows);
    }

    private boolean blank(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) return true;
        for (int column = 0; column < HEADERS.size(); column++)
            if (!value(row, column, formatter, evaluator).isBlank()) return false;
        return true;
    }

    private String value(Row row, int column, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null || row.getCell(column) == null) return "";
        return formatter.formatCellValue(row.getCell(column), evaluator).trim();
    }

    private Integer integer(String value, int rowNumber, String field) {
        if (!value.matches("\\d+"))
            throw invalid("Row " + rowNumber + ": " + field + " must be a whole number.");
        try {
            return Integer.valueOf(value);
        }
        catch (NumberFormatException exception) {
            throw invalid("Row " + rowNumber + ": " + field + " is outside the supported range.");
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VENDOR_PRINT_RESPONSE", message);
    }
}
