package com.example.chequebookrequestportal.corebanking.managerscheque;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountLookup.AccountDetails;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ManagersChequeAccountService {
    private static final Logger log = LoggerFactory.getLogger(ManagersChequeAccountService.class);
    private final ManagersChequeAccountLookup lookup;

    public ManagersChequeAccountService(ManagersChequeAccountLookup lookup) {
        this.lookup = lookup;
    }

    public AccountDetails resolve(String configuredAccount, String branchCode) {
        String lookupId = UUID.randomUUID().toString();
        log.info("Manager's Cheque GAM enquiry request: lookupId={}, branchCode={}, configuredAccount={}",
                lookupId, branchCode, mask(configuredAccount));

        List<AccountDetails> rows;
        try {
            rows = lookup.findConfiguredAccount(configuredAccount);
        }
        catch (DataAccessException exception) {
            log.error("Manager's Cheque GAM enquiry response: lookupId={}, branchCode={}, status=UNAVAILABLE",
                    lookupId, branchCode, exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MANAGERS_CHEQUE_ENQUIRY_UNAVAILABLE",
                    "The Manager's Cheque account could not be verified because Finacle is unavailable.", true);
        }

        if (rows.isEmpty()) {
            log.warn("Manager's Cheque GAM enquiry response: lookupId={}, branchCode={}, status=NOT_FOUND, rows=0",
                    lookupId, branchCode);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MANAGERS_CHEQUE_ACCOUNT_NOT_FOUND",
                    "The configured Manager's Cheque account was not found for your branch.");
        }
        if (rows.size() != 1) {
            log.error("Manager's Cheque GAM enquiry response: lookupId={}, branchCode={}, status=AMBIGUOUS, rows={}",
                    lookupId, branchCode, rows.size());
            throw new ApiException(HttpStatus.CONFLICT, "MANAGERS_CHEQUE_ACCOUNT_AMBIGUOUS",
                    "Multiple Manager's Cheque accounts matched the configured branch account.");
        }

        AccountDetails row = normalize(rows.get(0));
        try {
            validate(row, configuredAccount);
        }
        catch (ApiException exception) {
            log.warn("Manager's Cheque GAM enquiry response: lookupId={}, branchCode={}, status=INVALID, code={}",
                    lookupId, branchCode, exception.getCode());
            throw exception;
        }
        log.info("Manager's Cheque GAM enquiry response: lookupId={}, branchCode={}, status=SUCCESS, rows=1, "
                        + "accountNumber={}, schemeCode={}, currency={}, accountOwnership={}",
                lookupId, branchCode, mask(row.accountNumber()), row.schemeCode(), row.currency(),
                row.accountOwnership());
        return row;
    }

    private AccountDetails normalize(AccountDetails row) {
        return new AccountDetails(trim(row.accountNumber()), trim(row.accountName()), trim(row.accountOwnership()),
                trim(row.schemeCode()), trim(row.currency()));
    }

    private void validate(AccountDetails row, String configuredAccount) {
        if (blank(row.accountNumber()) || !row.accountNumber().matches("\\d{10}")
                || !configuredAccount.equals(row.accountNumber()))
            invalid("Finacle returned a different account from the branch configuration.");
        if (blank(row.accountName()) || blank(row.accountOwnership()) || blank(row.schemeCode())
                || blank(row.currency()))
            invalid("Finacle returned incomplete Manager's Cheque account details.");
        if (!"O".equalsIgnoreCase(row.accountOwnership()))
            invalid("Finacle did not return the required Manager's Cheque account ownership.");
        if (!row.currency().matches("[A-Za-z]{3}"))
            invalid("Finacle returned an invalid currency for the Manager's Cheque account.");
    }

    private void invalid(String message) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MANAGERS_CHEQUE_ACCOUNT_DATA_INVALID", message);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String mask(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{10}")) return "**********";
        return accountNumber.substring(0, 3) + "****" + accountNumber.substring(7);
    }
}
