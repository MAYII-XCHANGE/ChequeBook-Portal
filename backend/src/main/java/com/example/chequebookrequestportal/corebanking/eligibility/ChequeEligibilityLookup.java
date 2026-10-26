package com.example.chequebookrequestportal.corebanking.eligibility;

public interface ChequeEligibilityLookup {
    Result findChequeAllowedFlag(String accountNumber);

    record Result(boolean found, String flag) {
    }
}
