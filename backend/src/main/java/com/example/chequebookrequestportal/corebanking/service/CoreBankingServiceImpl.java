package com.example.chequebookrequestportal.corebanking.service;

import com.example.chequebookrequestportal.common.ApiException;
import com.example.chequebookrequestportal.corebanking.client.CoreBankingClient;
import com.example.chequebookrequestportal.corebanking.eligibility.ChequeEligibilityService;
import com.example.chequebookrequestportal.corebanking.dto.request.CreditTransferRequest;
import com.example.chequebookrequestportal.corebanking.dto.request.CreditTransferRequest.PartTransaction;
import com.example.chequebookrequestportal.corebanking.dto.request.ChequeBookChargePosting;
import com.example.chequebookrequestportal.corebanking.dto.request.InternalTransferRequest;
import com.example.chequebookrequestportal.corebanking.dto.request.NameEnquiryRequest;
import com.example.chequebookrequestportal.corebanking.dto.response.CreditTransferResponse;
import com.example.chequebookrequestportal.corebanking.dto.response.InternalTransferResponse;
import com.example.chequebookrequestportal.corebanking.dto.response.NameEnquiryResponse;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CoreBankingServiceImpl implements CoreBankingService {
    private static final String CHARGE_REMARK = "CHEQUE BOOK ISSUANCE FEE";
    private static final String VAT_REMARK = "VAT FEE";
    private static final String CUSTOMER_REMARK = CHARGE_REMARK + " + VAT";
    private static final String REVERSAL_PREFIX = "REVERSAL - ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter CORE_BANKING_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final CoreBankingClient client;
    private final ChequeEligibilityService chequeEligibility;
    private final String transactionCurrency;
    private final String transactionSubType;
    private final String transactionType;
    private final String partTransactionType;

    public CoreBankingServiceImpl(
            CoreBankingClient client,
            ChequeEligibilityService chequeEligibility,
            @Value("${core-banking.transaction-currency:NGN}") String transactionCurrency,
            @Value("${core-banking.transaction-sub-type:CI}") String transactionSubType,
            @Value("${core-banking.transaction-type:NIP}") String transactionType,
            @Value("${core-banking.part-transaction-type:AMT}") String partTransactionType) {
        this.client = client;
        this.chequeEligibility = chequeEligibility;
        this.transactionCurrency = transactionCurrency;
        this.transactionSubType = transactionSubType;
        this.transactionType = transactionType;
        this.partTransactionType = partTransactionType;
    }

    @Override
    public NameEnquiryResponse nameEnquiry(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{10}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_NUMBER",
                    "A valid 10-digit account number is required.");
        chequeEligibility.requireChequeAllowed(accountNumber);
        NameEnquiryResponse response = client.nameEnquiry(new NameEnquiryRequest(accountNumber));
        if (response == null || !response.successful() || response.accountName() == null
                || response.accountName().isBlank()) {
            String message = response != null && response.status() != null && response.status().message() != null
                    ? response.status().message()
                    : "Core banking could not confirm this account.";
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NAME_ENQUIRY_FAILED", message);
        }
        return response;
    }

    @Override
    public InternalTransferResponse transfer(InternalTransferRequest request) {
        validate(request);
        return submit(reference -> toCreditTransfer(request, reference));
    }

    @Override
    public InternalTransferResponse postChequeBookCharge(ChequeBookChargePosting request) {
        validate(request);
        return submit(reference -> toCreditTransfer(request, reference, false));
    }

    @Override
    public InternalTransferResponse reverseChequeBookCharge(ChequeBookChargePosting request) {
        validate(request);
        return submit(reference -> toCreditTransfer(request, reference, true));
    }

    private InternalTransferResponse submit(Function<String, CreditTransferRequest> buildRequest) {
        String transferReference = numericReference(30);
        CreditTransferResponse response = client.creditTransfer(buildRequest.apply(transferReference));
        if (hasResponseCode(response, "26")) {
            transferReference = numericReference(30);
            response = client.creditTransfer(buildRequest.apply(transferReference));
        }
        return toInternalTransferResponse(response, transferReference);
    }

    private CreditTransferRequest toCreditTransfer(InternalTransferRequest request, String transferReference) {
        String amount = request.amount().stripTrailingZeros().toPlainString();
        return creditTransfer(transferReference, List.of(
                new PartTransaction(request.debitAccount(), amount, request.remark(), "D",
                        request.narration(), partTransactionType),
                new PartTransaction(request.creditAccount(), amount, request.remark(), "C",
                        request.narration(), partTransactionType)));
    }

    private CreditTransferRequest toCreditTransfer(ChequeBookChargePosting request, String transferReference,
            boolean reversal) {
        String total = request.chargeAmount().add(request.vatAmount()).stripTrailingZeros().toPlainString();
        String charge = request.chargeAmount().stripTrailingZeros().toPlainString();
        String vat = request.vatAmount().stripTrailingZeros().toPlainString();
        return creditTransfer(transferReference, reversal
                ? List.of(
                        new PartTransaction(request.chargeGlAccount(), charge, REVERSAL_PREFIX + CHARGE_REMARK, "D",
                                request.narration(), partTransactionType),
                        new PartTransaction(request.vatGlAccount(), vat, REVERSAL_PREFIX + VAT_REMARK, "D",
                                request.narration(), partTransactionType),
                        new PartTransaction(request.customerAccount(), total, REVERSAL_PREFIX + CUSTOMER_REMARK, "C",
                                request.narration(), partTransactionType))
                : List.of(
                        new PartTransaction(request.customerAccount(), total, CUSTOMER_REMARK, "D",
                                request.narration(), partTransactionType),
                        new PartTransaction(request.chargeGlAccount(), charge, CHARGE_REMARK, "C",
                                request.narration(), partTransactionType),
                        new PartTransaction(request.vatGlAccount(), vat, VAT_REMARK, "C",
                                request.narration(), partTransactionType)));
    }

    private CreditTransferRequest creditTransfer(String transferReference, List<PartTransaction> entries) {
        return new CreditTransferRequest(
                transferReference,
                LocalDateTime.now().format(CORE_BANKING_DATE),
                transactionCurrency,
                LocalDate.now().toString(),
                transactionSubType,
                transactionType,
                transferReference,
                entries);
    }

    private InternalTransferResponse toInternalTransferResponse(CreditTransferResponse response,
            String fallbackReference) {
        CreditTransferResponse.Status status = response == null ? null : response.status();
        return new InternalTransferResponse(
                response == null || response.transactionReference() == null || response.transactionReference().isBlank()
                        ? fallbackReference : response.transactionReference(),
                response == null ? null : response.requestId(),
                response == null ? null : response.transactionId(),
                response == null ? null : response.messageDate(),
                response == null ? null : response.transactionDate(),
                status == null ? null : status.type(),
                status == null ? null : status.code(),
                status == null ? null : status.message());
    }

    private boolean hasResponseCode(CreditTransferResponse response, String code) {
        return response != null && response.status() != null && code.equals(response.status().code());
    }

    private void validate(InternalTransferRequest request) {
        if (request == null || request.debitAccount() == null || request.debitAccount().isBlank()
                || request.creditAccount() == null || request.creditAccount().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_ACCOUNTS",
                    "Debit and credit accounts are required.");
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_AMOUNT",
                    "Transfer amount must be greater than zero.");
    }

    private void validate(ChequeBookChargePosting request) {
        if (request == null || request.customerAccount() == null || request.customerAccount().isBlank()
                || request.chargeGlAccount() == null || request.chargeGlAccount().isBlank()
                || request.vatGlAccount() == null || request.vatGlAccount().isBlank()
                || request.customerAccount().equals(request.chargeGlAccount())
                || request.customerAccount().equals(request.vatGlAccount())
                || request.chargeGlAccount().equals(request.vatGlAccount()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHARGE_ACCOUNTS",
                    "Distinct customer, charge GL and VAT GL accounts are required.");
        if (request.chargeAmount() == null || request.chargeAmount().compareTo(BigDecimal.ZERO) <= 0
                || request.vatAmount() == null || request.vatAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHARGE_AMOUNTS",
                    "Cheque book charge and VAT amounts must be greater than zero.");
    }

    private String numericReference(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++)
            value.append(RANDOM.nextInt(10));
        return value.toString();
    }
}
