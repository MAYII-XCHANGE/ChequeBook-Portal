package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.ChequeTypeSequenceLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChequeTypeSequenceLockRepository extends JpaRepository<ChequeTypeSequenceLock, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select typeLock from ChequeTypeSequenceLock typeLock where typeLock.chequeTypeCode = :code")
    Optional<ChequeTypeSequenceLock> findByCodeForUpdate(@Param("code") String code);
}
