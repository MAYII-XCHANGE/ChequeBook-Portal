package com.example.chequebookrequestportal.corebanking.service;

import com.example.chequebookrequestportal.corebanking.dto.request.InternalTransferRequest;
import com.example.chequebookrequestportal.corebanking.dto.request.ChequeBookChargePosting;
import com.example.chequebookrequestportal.corebanking.dto.response.InternalTransferResponse;
import com.example.chequebookrequestportal.corebanking.dto.response.NameEnquiryResponse;

public interface CoreBankingService {
    NameEnquiryResponse nameEnquiry(String accountNumber);

    InternalTransferResponse transfer(InternalTransferRequest request);

    InternalTransferResponse postChequeBookCharge(ChequeBookChargePosting request);

    InternalTransferResponse reverseChequeBookCharge(ChequeBookChargePosting request);
}
