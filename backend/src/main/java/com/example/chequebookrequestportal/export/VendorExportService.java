package com.example.chequebookrequestportal.export;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.common.BusinessIdentifier;
import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.DebitStatus;
import com.example.chequebookrequestportal.domain.RequestHistory;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.domain.VendorExportBatch;
import com.example.chequebookrequestportal.domain.VendorExportItem;
import com.example.chequebookrequestportal.repository.ChequeRequestRepository;
import com.example.chequebookrequestportal.repository.ChargeTransactionRepository;
import com.example.chequebookrequestportal.repository.RequestHistoryRepository;
import com.example.chequebookrequestportal.repository.VendorExportBatchRepository;
import com.example.chequebookrequestportal.repository.VendorExportItemRepository;
import com.example.chequebookrequestportal.security.SecurityService;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class VendorExportService {
    private static final Logger log = LoggerFactory.getLogger(VendorExportService.class);
    private static final String[] HEADERS = { "S/N", "Account Number", "Account Name", "Branch Name",
            "Branch Address", "Sort Code", "Cheque Type", "No of Leaves", "No of Booklets", "Delivery SolID",
            "Delivery Address", "Scheme Code", "Currency", "Range Start", "Range Stop" };
    private final VendorExportBatchRepository batches;
    private final VendorExportItemRepository items;
    private final ChequeRequestRepository requests;
    private final ChargeTransactionRepository charges;
    private final RequestHistoryRepository history;
    private final SecurityService security;
    private final AuditService audit;
    private final Path outputDirectory;
    private final String deliverySol, deliveryAddress, schemeOverride;

    public VendorExportService(VendorExportBatchRepository b, VendorExportItemRepository i, ChequeRequestRepository r,
            ChargeTransactionRepository c, RequestHistoryRepository h, SecurityService s, AuditService a,
            @Value("${app.vendor.output-directory}") String directory,
            @Value("${app.vendor.delivery-sol-id}") String sol, @Value("${app.vendor.delivery-address}") String address,
            @Value("${app.vendor.scheme-code-override:}") String scheme) {
        batches = b;
        items = i;
        requests = r;
        charges = c;
        history = h;
        security = s;
        audit = a;
        outputDirectory = Path.of(directory).toAbsolutePath().normalize();
        deliverySol = sol;
        deliveryAddress = address;
        schemeOverride = scheme;
    }

    public List<Map<String, Object>> eligible() {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        return requests.findExportableByStatus(RequestStatus.APPROVED, DebitStatus.SUCCESSFUL).stream()
                .map(this::requestMap).toList();
    }

    @Transactional
    public Map<String, Object> create(List<String> requestNumbers) {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        if (requestNumbers == null || requestNumbers.isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_EXPORT", "Select at least one approved request.");
        StaffUser actor = security.user();
        List<ChequeRequest> selected = requestNumbers.stream().distinct()
                .map(n -> requests.findByRequestNumberForUpdate(n).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "REQUEST_NOT_FOUND", "Request " + n + " was not found.")))
                .toList();
        for (ChequeRequest r : selected) {
            if (r.getStatus() != RequestStatus.APPROVED
                    || !charges.existsByRequestRequestNumberAndStatus(r.getRequestNumber(), DebitStatus.SUCCESSFUL)
                    || items.existsByRequestRequestNumber(r.getRequestNumber()))
                throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_EXPORTABLE",
                        r.getRequestNumber() + " is not eligible for export.");
            requireChequeType(r);
        }
        String provisionalFileName = ".pending-" + UUID.randomUUID() + ".tmp";
        VendorExportBatch batch = batches.saveAndFlush(
                new VendorExportBatch(selected.size(), actor, provisionalFileName));
        String batchNumber = BusinessIdentifier.vendorExport(LocalDate.now(ZoneOffset.UTC), batch.getId());
        batch.assignIdentity(batchNumber);
        batches.flush();
        String fileName = batch.getFileName();
        List<VendorExportItem> snapshots = selected.stream().map(r -> {
            String scheme = schemeOverride.isBlank() ? r.getSchemeCode() : schemeOverride;
            return items.save(new VendorExportItem(batch, r, requireChequeType(r), deliverySol, deliveryAddress,
                    scheme));
        }).toList();
        writeWorkbook(fileName, snapshots);
        Path workbook = outputDirectory.resolve(fileName).normalize();
        deleteWorkbookIfTransactionRollsBack(workbook);
        batch.setChecksum(checksum(workbook));
        for (ChequeRequest r : selected) {
            r.setStatus(RequestStatus.EXPORTED);
            history.save(new RequestHistory(r, RequestStatus.APPROVED.name(), RequestStatus.EXPORTED.name(),
                    actor.getFullName(), "Included in vendor export " + batchNumber + "."));
        }
        audit.record("VENDOR_EXPORT_CREATED", "VENDOR_EXPORT", batchNumber,
                "Generated " + selected.size() + " vendor records.");
        return batchMap(batch);
    }

    public Download download(String batchNumber) {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        VendorExportBatch b = batch(batchNumber);
        Path file = outputDirectory.resolve(b.getFileName()).normalize();
        if (!file.startsWith(outputDirectory))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "Invalid export file path.");
        try {
            audit.record("VENDOR_EXPORT_DOWNLOADED", "VENDOR_EXPORT", batchNumber, "Vendor export downloaded.");
            return new Download(b.getFileName(), new ByteArrayResource(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EXPORT_FILE_NOT_FOUND", "The export file is unavailable.");
        }
    }

    @Transactional
    public Map<String, Object> confirmSent(String batchNumber) {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        VendorExportBatch b = lockedBatch(batchNumber);
        if (!VendorExportBatch.STATUS_EXPORTED.equals(b.getStatus()))
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_BATCH_STATUS",
                    "Only an exported batch can be marked sent.");
        StaffUser actor = security.user();
        b.confirmSent(actor);
        for (VendorExportItem item : items.findByBatchBatchNumberOrderByIdAsc(batchNumber)) {
            ChequeRequest r = item.getRequest();
            if (r.getStatus() != RequestStatus.EXPORTED)
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                        r.getRequestNumber() + " is not exported.");
            r.setStatus(RequestStatus.SENT_TO_VENDOR);
            history.save(new RequestHistory(r, RequestStatus.EXPORTED.name(), RequestStatus.SENT_TO_VENDOR.name(),
                    actor.getFullName(), "Vendor transmission confirmed for " + batchNumber + "."));
        }
        audit.record("VENDOR_EXPORT_SENT", "VENDOR_EXPORT", batchNumber, "Manual transmission to vendor confirmed.");
        return batchMap(b);
    }

    public Map<String, Object> confirmPrinted(String batchNumber) {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        security.requirePermission(Permissions.PROCESS_REQUEST);
        batch(batchNumber);
        throw new ApiException(HttpStatus.CONFLICT, "VENDOR_PRINT_RESPONSE_REQUIRED",
                "Upload the vendor print response with cheque serial ranges instead of confirming printing manually.");
    }

    public List<Map<String, Object>> list() {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        return batches.findAllByOrderByCreatedAtDesc().stream().map(this::batchMap).toList();
    }

    public Map<String, Object> get(String batchNumber) {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        VendorExportBatch batch = batch(batchNumber);
        Map<String, Object> result = new java.util.LinkedHashMap<>(batchMap(batch));
        List<VendorExportItem> batchItems = items.findByBatchBatchNumberOrderByIdAsc(batchNumber);
        result.put("rows", java.util.stream.IntStream.range(0, batchItems.size())
                .mapToObj(index -> batchItems.get(index).row(index + 1)).toList());
        return result;
    }

    private void writeWorkbook(String fileName, List<VendorExportItem> rows) {
        Path workbook = outputDirectory.resolve(fileName).normalize();
        if (!workbook.startsWith(outputDirectory))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "Invalid export file path.");
        try {
            Files.createDirectories(outputDirectory);
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet("Sheet1");
                sheet.createFreezePane(0, 1);
                CellStyle header = wb.createCellStyle();
                header.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
                header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                var font = wb.createFont();
                font.setBold(true);
                header.setFont(font);
                CellStyle text = wb.createCellStyle();
                text.setDataFormat(wb.createDataFormat().getFormat("@"));
                var hr = sheet.createRow(0);
                for (int c = 0; c < HEADERS.length; c++) {
                    var cell = hr.createCell(c);
                    cell.setCellValue(HEADERS[c]);
                    cell.setCellStyle(header);
                }
                for (int x = 0; x < rows.size(); x++) {
                    var row = sheet.createRow(x + 1);
                    String[] values = rows.get(x).row(x + 1);
                    for (int c = 0; c < values.length; c++) {
                        var cell = row.createCell(c);
                        cell.setCellStyle(text);
                        cell.setCellValue(values[c] == null ? "" : values[c]);
                    }
                }
                for (int c = 0; c < HEADERS.length; c++) {
                    sheet.autoSizeColumn(c);
                    sheet.setColumnWidth(c, Math.min(Math.max(sheet.getColumnWidth(c) + 512, 3000), 15000));
                }
                try (OutputStream out = Files.newOutputStream(workbook)) {
                    wb.write(out);
                }
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(workbook);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_GENERATION_FAILED",
                    "The vendor workbook could not be generated.", true);
        }
    }

    private void deleteWorkbookIfTransactionRollsBack(Path workbook) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED)
                    return;
                try {
                    Files.deleteIfExists(workbook);
                } catch (IOException exception) {
                    log.error("Could not remove rolled-back vendor export file {}", workbook, exception);
                }
            }
        });
    }

    private String checksum(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CHECKSUM_FAILED",
                    "The export checksum could not be generated.");
        }
    }

    private VendorExportBatch batch(String n) {
        return batches.findByBatchNumber(n).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND", "Vendor export batch not found."));
    }

    private VendorExportBatch lockedBatch(String number) {
        return batches.findByBatchNumberForUpdate(number).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND", "Vendor export batch not found."));
    }

    private Map<String, Object> batchMap(VendorExportBatch b) {
        return Map.of("batchNumber", b.getBatchNumber(), "fileName", b.getFileName(), "recordCount", b.getRecordCount(),
                "status", b.getStatus(), "checksum", b.getFileChecksum() == null ? "" : b.getFileChecksum(),
                "createdAt", b.getCreatedAt(), "sentAt", b.getSentAt() == null ? "" : b.getSentAt());
    }

    private Map<String, Object> requestMap(ChequeRequest r) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", r.getRequestNumber());
        result.put("customer", r.getAccountName());
        result.put("accountNumber", r.getAccountNumber());
        result.put("branch", r.getBranchName());
        result.put("createdAt", r.getCreatedAt());
        result.put("status", r.getStatus());
        result.put("createdBy", r.getCreatedBy().getFullName());
        result.put("leafCount", r.getLeafCount());
        result.put("chequeTypeCode", r.getChequeType() == null ? null : r.getChequeType().code());
        result.put("chequeTypeLabel", r.getChequeType() == null ? null : r.getChequeType().label());
        return result;
    }

    String requireChequeType(ChequeRequest request) {
        if (request.getChequeType() == null)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CHEQUE_TYPE_REQUIRED",
                    request.getRequestNumber()
                            + " has no cheque type. Backfill it before creating a vendor export.");
        return request.getChequeType().code();
    }

    public record Download(String fileName, ByteArrayResource resource) {
    }
}
