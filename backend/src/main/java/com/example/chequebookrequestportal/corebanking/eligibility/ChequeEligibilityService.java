package com.example.chequebookrequestportal.corebanking.eligibility;

import com.example.chequebookrequestportal.audit.AuditService;
import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.email.WorkflowEmailService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChequeEligibilityService {
    private static final Logger log = LoggerFactory.getLogger(ChequeEligibilityService.class);
    private final ChequeEligibilityLookup lookup;
    private final AuditService audit;
    private final WorkflowEmailService email;

    public ChequeEligibilityService(ChequeEligibilityLookup lookup, AuditService audit,
            WorkflowEmailService email) {
        this.lookup = lookup;
        this.audit = audit;
        this.email = email;
    }

    public void requireChequeAllowed(String accountNumber) {
        ChequeEligibilityLookup.Result result;
        try {
            result = lookup.findChequeAllowedFlag(accountNumber);
        }
        catch (DataAccessException exception) {
            log.error("Finacle cheque-eligibility lookup failed for account ending {}", ending(accountNumber),
                    exception);
            throw unavailable("Cheque eligibility could not be verified because Finacle is unavailable.");
        }
        if (!result.found())
            throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                    "The account was not found in Finacle.");

        String flag = result.flag() == null ? "" : result.flag().trim().toUpperCase(Locale.ROOT);
        if ("Y".equals(flag)) return;
        if ("N".equals(flag))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_CHEQUE_ENABLED",
                    "This account is not enabled for cheque-book issuance.");

        recordReview(accountNumber, flag);
        throw new ApiException(HttpStatus.CONFLICT, "CHEQUE_ELIGIBILITY_REVIEW_REQUIRED",
                "Finacle returned an unexpected cheque-eligibility value. Operations review is required.");
    }

    private void recordReview(String accountNumber, String flag) {
        String displayedFlag = flag.isBlank() ? "NULL/BLANK" : flag;
        try {
            audit.record("CHEQUE_ELIGIBILITY_REVIEW_REQUIRED", "ACCOUNT", masked(accountNumber),
                    "Finacle returned unexpected chq_alwd_flg value " + displayedFlag + ".");
        }
        catch (RuntimeException exception) {
            log.error("Could not persist the cheque-eligibility review audit event", exception);
        }
        try {
            email.chequeEligibilityReviewRequired(accountNumber, displayedFlag);
        }
        catch (RuntimeException exception) {
            log.error("Could not queue the cheque-eligibility Operations notification", exception);
        }
    }

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHEQUE_ELIGIBILITY_UNAVAILABLE", message, true);
    }

    private String ending(String accountNumber) {
        return accountNumber == null || accountNumber.length() < 4
                ? "unknown" : accountNumber.substring(accountNumber.length() - 4);
    }

    private String masked(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{10}")) return "**********";
        return accountNumber.substring(0, 3) + "****" + accountNumber.substring(7);
    }
}
