package com.example.chequebookrequestportal.export;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record VendorPrintResponseInput(@NotEmpty List<@Valid Row> rows) {
    public record Row(
            String serialNumber,
            String accountNumber,
            String accountName,
            String branchName,
            String branchAddress,
            String sortCode,
            String chequeType,
            Integer numberOfLeaves,
            Integer numberOfBooklets,
            String deliverySolId,
            String deliveryAddress,
            String schemeCode,
            String currency,
            String rangeStart,
            String rangeStop) {
    }
}
