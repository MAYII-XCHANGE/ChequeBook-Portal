package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.ChargeTransaction;
import com.example.chequebookrequestportal.domain.DebitStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeTransactionRepository extends JpaRepository<ChargeTransaction, Long> {
    Optional<ChargeTransaction> findByRequestRequestNumber(String requestNumber);

    Optional<ChargeTransaction> findByIdempotencyKey(String key);

    boolean existsByRequestRequestNumberAndStatus(String requestNumber, DebitStatus status);
}
