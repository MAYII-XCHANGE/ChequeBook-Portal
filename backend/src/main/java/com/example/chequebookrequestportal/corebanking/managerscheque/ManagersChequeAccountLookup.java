package com.example.chequebookrequestportal.corebanking.managerscheque;

import java.util.List;

public interface ManagersChequeAccountLookup {
    List<AccountDetails> findConfiguredAccount(String configuredAccount);

    record AccountDetails(String accountNumber, String accountName, String accountOwnership,
            String schemeCode, String currency) {
    }
}
