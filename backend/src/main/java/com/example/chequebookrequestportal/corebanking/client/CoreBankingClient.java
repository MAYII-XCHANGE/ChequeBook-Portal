package com.example.chequebookrequestportal.corebanking.client;

import com.example.chequebookrequestportal.corebanking.dto.request.CreditTransferRequest;
import com.example.chequebookrequestportal.corebanking.dto.request.NameEnquiryRequest;
import com.example.chequebookrequestportal.corebanking.dto.response.CreditTransferResponse;
import com.example.chequebookrequestportal.corebanking.dto.response.NameEnquiryResponse;

public interface CoreBankingClient {
    NameEnquiryResponse nameEnquiry(NameEnquiryRequest request);

    CreditTransferResponse creditTransfer(CreditTransferRequest request);
}
