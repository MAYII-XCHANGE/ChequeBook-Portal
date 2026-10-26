package com.example.chequebookrequestportal.export;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.request.ChequeBookResponse;
import com.example.chequebookrequestportal.security.SecurityService;
import com.example.chequebookrequestportal.service.ChequeBookService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class VendorPrintWorkflowService {
    private final VendorPrintPersistenceService persistence;
    private final ChequeBookService chequeBooks;
    private final SecurityService security;

    public VendorPrintWorkflowService(VendorPrintPersistenceService persistence, ChequeBookService chequeBooks,
            SecurityService security) {
        this.persistence = persistence;
        this.chequeBooks = chequeBooks;
        this.security = security;
    }

    public Map<String, Object> importAndRegister(String batchNumber, VendorPrintResponseInput input) {
        security.requirePermission(Permissions.EXPORT_VENDOR);
        security.requirePermission(Permissions.PROCESS_REQUEST);
        VendorPrintPersistenceService.ImportResult imported = persistence.importRows(batchNumber, input.rows());
        List<Map<String, Object>> registrationResults = new ArrayList<>();
        for (String requestNumber : imported.registrations())
            registrationResults.add(register(requestNumber, false));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNumber", batchNumber);
        result.put("importedRows", imported.importedRows());
        result.put("registrations", registrationResults);
        return result;
    }

    public Map<String, Object> retry(String requestNumber) {
        security.requirePermission(Permissions.PROCESS_REQUEST);
        return register(requestNumber, true);
    }

    public Map<String, Object> reconcile(String requestNumber, FinacleReconciliationInput input) {
        return persistence.reconcile(requestNumber, input);
    }

    public List<Map<String, Object>> batchAllocations(String batchNumber) {
        return persistence.batchAllocations(batchNumber);
    }

    private Map<String, Object> register(String requestNumber, boolean retry) {
        VendorPrintPersistenceService.RegistrationCommand command = persistence.beginRegistration(requestNumber, retry);
        if (command == null)
            return persistence.allocation(requestNumber);
        ChequeBookResponse response;
        try {
            response = chequeBooks.requestChequeBook(command.accountNumber(), command.leafCount(),
                    command.branchCode(), command.startChequeNumber());
        }
        catch (ApiException exception) {
            persistence.requireRegistrationReview(requestNumber, exception.getCode(), exception.getMessage());
            return persistence.allocation(requestNumber);
        }
        catch (RuntimeException exception) {
            persistence.requireRegistrationReview(requestNumber, "FINACLE_UNEXPECTED_ERROR",
                    "Finacle registration ended without a confirmed outcome.");
            return persistence.allocation(requestNumber);
        }
        if (response.hostSuccessful())
            persistence.completeRegistration(requestNumber, response);
        else if (isConfirmedHostFailure(response))
            persistence.failRegistration(requestNumber, response);
        else
            persistence.requireRegistrationReview(requestNumber, responseCode(response), response.failureMessage());
        return persistence.allocation(requestNumber);
    }

    private boolean isConfirmedHostFailure(ChequeBookResponse response) {
        if (response.data() == null || response.data().hostTransactionStatus() == null)
            return false;
        String status = response.data().hostTransactionStatus().trim().toUpperCase(java.util.Locale.ROOT);
        return status.equals("FAILED") || status.equals("FAILURE") || status.equals("REJECTED");
    }

    private String responseCode(ChequeBookResponse response) {
        return response.data() == null ? null : response.data().errorCode();
    }
}
