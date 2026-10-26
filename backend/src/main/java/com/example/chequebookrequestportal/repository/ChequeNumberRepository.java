package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.ChequeNumber;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChequeNumberRepository extends JpaRepository<ChequeNumber, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select counter from ChequeNumber counter where counter.id = :id")
    Optional<ChequeNumber> findForUpdate(@Param("id") long id);
}
