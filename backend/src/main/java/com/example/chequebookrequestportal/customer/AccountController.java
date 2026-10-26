package com.example.chequebookrequestportal.customer;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.common.Permissions;
import com.example.chequebookrequestportal.corebanking.dto.response.NameEnquiryResponse;
import com.example.chequebookrequestportal.corebanking.service.CoreBankingService;
import com.example.chequebookrequestportal.domain.Branch;
import com.example.chequebookrequestportal.domain.StaffUser;
import com.example.chequebookrequestportal.security.SecurityService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final CoreBankingService coreBanking;
    private final SecurityService security;

    public AccountController(CoreBankingService coreBanking, SecurityService security) {
        this.coreBanking = coreBanking;
        this.security = security;
    }

    @GetMapping("/{accountNumber}/eligibility")
    @PreAuthorize("hasAuthority('CREATE_CHEQUE_REQUEST')")
    Map<String, Object> eligibility(
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = "\\d{10}") String accountNumber) {
        security.requirePermission(Permissions.CREATE_REQUEST);
        StaffUser actor = security.user();
        Branch actorBranch = actor.getBranch();
        if (actorBranch == null)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "USER_BRANCH_REQUIRED",
                    "The initiating user must be assigned to a branch.");

        NameEnquiryResponse account = coreBanking.nameEnquiry(accountNumber);
        String accountHomeBranchCode = nullable(account.branchCode());
        String accountHomeBranchName = nullable(account.branchName());
        List<Object[]> checks = new ArrayList<>();
        checks.add(new Object[] { "Core-banking name enquiry", true });
        checks.add(new Object[] { "Account name returned", present(account.accountName()) });

        boolean eligible = present(account.accountName());
        if (present(account.accountStatus())) {
            boolean active = "ACTIVE".equalsIgnoreCase(account.accountStatus());
            checks.add(new Object[] { "Account is active", active });
            eligible &= active;
        }
        if (present(account.kycLevel())) {
            boolean kycComplete = "3".equalsIgnoreCase(account.kycLevel());
            checks.add(new Object[] { "KYC is complete", kycComplete });
            eligible &= kycComplete;
        }
        if (present(account.restrictions())) {
            boolean unrestricted = "NONE".equalsIgnoreCase(account.restrictions())
                    || "NO".equalsIgnoreCase(account.restrictions());
            checks.add(new Object[] { "No cheque restrictions", unrestricted });
            eligible &= unrestricted;
        }

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", account.accountName());
        customer.put("accountNumber", fallback(account.accountNumber(), accountNumber));
        customer.put("acctOwnership", account.acctOwnership());
        customer.put("accountStatus", title(account.accountStatus()));
        // Keep the legacy branch fields as account-home aliases for API compatibility.
        customer.put("branch", branchLabel(accountHomeBranchName, accountHomeBranchCode));
        customer.put("branchName", accountHomeBranchName);
        customer.put("branchCode", accountHomeBranchCode);
        customer.put("branchAddress", nullable(account.branchAddress()));
        customer.put("sortCode", nullable(account.sortCode()));
        customer.put("accountHomeBranch", branchLabel(accountHomeBranchName, accountHomeBranchCode));
        customer.put("accountHomeBranchName", accountHomeBranchName);
        customer.put("accountHomeBranchCode", accountHomeBranchCode);
        customer.put("servicingBranch", branchLabel(actorBranch.getName(), actorBranch.getCode()));
        customer.put("servicingBranchName", actorBranch.getName());
        customer.put("servicingBranchCode", actorBranch.getCode());
        customer.put("kycLevel", title(account.kycLevel()));
        customer.put("restrictions", title(account.restrictions()));
        customer.put("currency", account.currency());
        customer.put("schemeCode", account.schemeCode());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eligible", eligible);
        result.put("reason", eligible ? null : "The account failed one or more eligibility checks.");
        result.put("checks", checks);
        return Map.of("customer", customer, "eligibility", result);
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String fallback(String value, String fallback) {
        return present(value) ? value : fallback;
    }

    private String nullable(String value) {
        return present(value) ? value.trim() : null;
    }

    private String branchLabel(String name, String code) {
        if (present(name) && present(code))
            return name + " - " + code;
        return present(name) ? name : present(code) ? code : null;
    }

    private String title(String value) {
        if (!present(value))
            return null;
        String normalized = value.trim();
        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1).toLowerCase();
    }
}
