package com.example.chequebookrequestportal.config;

import com.example.chequebookrequestportal.domain.ChequeNumber;
import com.example.chequebookrequestportal.repository.ChequeNumberRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChequeNumberSeeder implements CommandLineRunner {
    private final ChequeNumberRepository counters;

    public ChequeNumberSeeder(ChequeNumberRepository counters) {
        this.counters = counters;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!counters.existsById(ChequeNumber.COUNTER_ID))
            counters.save(ChequeNumber.startingAtZero());
    }
}
