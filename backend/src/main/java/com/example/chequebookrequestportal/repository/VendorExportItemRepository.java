package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.VendorExportItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorExportItemRepository extends JpaRepository<VendorExportItem, Long> {
    List<VendorExportItem> findByBatchBatchNumberOrderByIdAsc(String batchNumber);

    boolean existsByRequestRequestNumber(String requestNumber);
}
