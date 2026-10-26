package com.example.chequebookrequestportal.corebanking.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NameEnquiryResponse(
        @JsonAlias({ "acct_no", "accountNumber" }) String accountNumber,
        @JsonAlias({ "acct_name", "accountName" }) String accountName,
        @JsonAlias({ "acct_bal", "accountBalance" }) String accountBalance,
        @JsonAlias({ "acct_ownership", "acctOwnership" }) String acctOwnership,
        @JsonAlias({ "acct_status", "accountStatus" }) String accountStatus,
        @JsonAlias({ "acct_ccy", "currency" }) String currency,
        @JsonAlias({ "scheme_code", "schemeCode" }) String schemeCode,
        @JsonAlias({ "sol_id", "branchCode" }) String branchCode,
        @JsonAlias({ "branch_name", "branchName" }) String branchName,
        @JsonAlias({ "branch_address", "branchAddress" }) String branchAddress,
        @JsonAlias({ "sort_code", "sortCode" }) String sortCode,
        @JsonAlias({ "acct_kyclevel", "kycLevel" }) String kycLevel,
        String restrictions,
        Status status) {

    public boolean successful() {
        if (status == null || status.type() == null)
            return false;
        String type = status.type().trim();
        return !"RJCT".equalsIgnoreCase(type) && !"FAIL".equalsIgnoreCase(type);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(String type, String code, String message) {
    }
}
