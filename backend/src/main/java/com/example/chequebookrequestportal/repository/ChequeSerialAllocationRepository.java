package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.ChequeSerialAllocation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChequeSerialAllocationRepository extends JpaRepository<ChequeSerialAllocation, Long> {
    Optional<ChequeSerialAllocation> findByRequestRequestNumber(String requestNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from ChequeSerialAllocation allocation where allocation.request.requestNumber = :number")
    Optional<ChequeSerialAllocation> findByRequestNumberForUpdate(@Param("number") String requestNumber);

    List<ChequeSerialAllocation> findByBatchBatchNumberOrderByIdAsc(String batchNumber);

    List<ChequeSerialAllocation> findByChequeTypeCode(String chequeTypeCode);
}
