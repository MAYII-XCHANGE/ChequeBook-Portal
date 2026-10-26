package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.VendorExportBatch;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorExportBatchRepository extends JpaRepository<VendorExportBatch, Long> {
    Optional<VendorExportBatch> findByBatchNumber(String number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from VendorExportBatch batch where batch.batchNumber = :number")
    Optional<VendorExportBatch> findByBatchNumberForUpdate(@Param("number") String number);

    List<VendorExportBatch> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
