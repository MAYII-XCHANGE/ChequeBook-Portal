package com.example.chequebookrequestportal.export;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vendor-exports")
@PreAuthorize("hasAuthority('EXPORT_VENDOR')")
public class VendorExportController {
    private final VendorExportService service;
    private final VendorPrintWorkflowService printWorkflow;
    private final VendorPrintResponseWorkbookParser workbookParser;

    public VendorExportController(VendorExportService s, VendorPrintWorkflowService printWorkflow,
            VendorPrintResponseWorkbookParser workbookParser) {
        service = s;
        this.printWorkflow = printWorkflow;
        this.workbookParser = workbookParser;
    }

    @GetMapping("/eligible-requests")
    Map<String, Object> eligible() {
        return Map.of("data", service.eligible());
    }

    @GetMapping
    List<Map<String, Object>> list() {
        return service.list();
    }

    @PostMapping
    Map<String, Object> create(@Valid @RequestBody ExportInput i) {
        return service.create(i.requestIds());
    }

    @GetMapping("/{batch}")
    Map<String, Object> get(@PathVariable String batch) {
        return service.get(batch);
    }

    @GetMapping("/{batch}/download")
    ResponseEntity<?> download(@PathVariable String batch) {
        var d = service.download(batch);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.fileName() + "\"")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(d.resource());
    }

    @PostMapping("/{batch}/confirm-sent")
    Map<String, Object> sent(@PathVariable String batch) {
        return service.confirmSent(batch);
    }

    @PostMapping("/{batch}/confirm-printed")
    @PreAuthorize("hasAuthority('EXPORT_VENDOR') and hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> printed(@PathVariable String batch) {
        return service.confirmPrinted(batch);
    }

    @PostMapping("/{batch}/print-response")
    @PreAuthorize("hasAuthority('EXPORT_VENDOR') and hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> importPrintResponse(@PathVariable String batch,
            @Valid @RequestBody VendorPrintResponseInput input) {
        return printWorkflow.importAndRegister(batch, input);
    }

    @PostMapping(value = "/{batch}/print-response-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EXPORT_VENDOR') and hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> importPrintResponseWorkbook(@PathVariable String batch,
            @RequestPart("file") MultipartFile file) {
        return printWorkflow.importAndRegister(batch, workbookParser.parse(file));
    }

    @GetMapping("/{batch}/print-response")
    Map<String, Object> printResponse(@PathVariable String batch) {
        return Map.of("data", printWorkflow.batchAllocations(batch));
    }

    @PostMapping("/registrations/{requestNumber}/retry")
    @PreAuthorize("hasAuthority('EXPORT_VENDOR') and hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> retryRegistration(@PathVariable String requestNumber) {
        return printWorkflow.retry(requestNumber);
    }

    @PostMapping("/registrations/{requestNumber}/reconcile")
    @PreAuthorize("hasAuthority('EXPORT_VENDOR') and hasAuthority('PROCESS_CHEQUE_REQUEST')")
    Map<String, Object> reconcileRegistration(@PathVariable String requestNumber,
            @Valid @RequestBody FinacleReconciliationInput input) {
        return printWorkflow.reconcile(requestNumber, input);
    }

    public record ExportInput(@NotEmpty List<@jakarta.validation.constraints.NotBlank String> requestIds) {
    }
}
