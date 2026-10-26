package com.example.chequebookrequestportal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.chequebookrequestportal.common.ApiException;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class VendorPrintResponseWorkbookParserTest {
    private static final String[] HEADERS = { "S/N", "Account Number", "Account Name", "Branch Name",
            "Branch Address", "Sort Code", "Cheque Type", "No of Leaves", "No of Booklets", "Delivery SolID",
            "Delivery Address", "Scheme Code", "Currency", "Range Start", "Range Stop" };
    private final VendorPrintResponseWorkbookParser parser = new VendorPrintResponseWorkbookParser();

    @Test
    void parsesAnExcelResponseAndPreservesLeadingZeroes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sheet1");
            var header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.length; column++)
                header.createCell(column).setCellValue(HEADERS[column]);
            var row = sheet.createRow(1);
            String[] values = { "1", "0123456789", "Customer", "Victoria Island", "1 Bank Street", "001", "01",
                    "50", "1", "001", "Victoria Island", "CUR", "NGN", "001001", "001050" };
            for (int column = 0; column < values.length; column++)
                row.createCell(column).setCellValue(values[column]);
            workbook.write(output);

            var input = parser.parse(new MockMultipartFile("file", "response.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()));

            assertEquals(1, input.rows().size());
            assertEquals("0123456789", input.rows().getFirst().accountNumber());
            assertEquals("001001", input.rows().getFirst().rangeStart());
            assertEquals(50, input.rows().getFirst().numberOfLeaves());
        }
    }

    @Test
    void rejectsAWorkbookWithDifferentHeaders() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("Account Number");
            workbook.write(output);

            ApiException exception = assertThrows(ApiException.class, () -> parser.parse(new MockMultipartFile(
                    "file", "response.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray())));

            assertEquals("INVALID_VENDOR_PRINT_RESPONSE", exception.getCode());
        }
    }
}
