package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.ChequeRequest;
import com.example.chequebookrequestportal.domain.DebitStatus;
import com.example.chequebookrequestportal.domain.RequestStatus;
import com.example.chequebookrequestportal.domain.ChequeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChequeRequestRepository
        extends JpaRepository<ChequeRequest, Long>, JpaSpecificationExecutor<ChequeRequest> {
    Optional<ChequeRequest> findByRequestNumber(String requestNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ChequeRequest request where request.requestNumber = :requestNumber")
    Optional<ChequeRequest> findByRequestNumberForUpdate(@Param("requestNumber") String requestNumber);

    boolean existsByAccountNumberAndStatusNotIn(String accountNumber, Collection<RequestStatus> statuses);

    boolean existsByBranchCodeIgnoreCaseAndChequeTypeAndStatusNotIn(String branchCode, ChequeType chequeType,
            Collection<RequestStatus> statuses);

    List<ChequeRequest> findByStatusAndBranchCodeIgnoreCaseOrderByCreatedAtAsc(RequestStatus status,
            String branchCode);

    List<ChequeRequest> findByStatusOrderByCreatedAtAsc(RequestStatus status);

    long countByStatus(RequestStatus status);

    @Query("""
            select request from ChequeRequest request
            where request.status in :statuses
              and (request.debitPolicy = com.example.chequebookrequestportal.domain.DebitPolicy.NOT_REQUIRED or exists (
                  select charge.id from ChargeTransaction charge
                  where charge.request = request and charge.status = :debitStatus
              ))
            order by request.createdAt asc
            """)
    List<ChequeRequest> findProcessingQueue(@Param("statuses") Collection<RequestStatus> statuses,
            @Param("debitStatus") DebitStatus debitStatus);

    long countByStatusAndBranchCodeIgnoreCase(RequestStatus status, String branchCode);

    long countByStatusAndBranchCodeIgnoreCaseAndChequeType(RequestStatus status, String branchCode,
            ChequeType chequeType);

    @Query("""
            select count(request) from ChequeRequest request
            where request.status in :statuses
              and (request.debitPolicy = com.example.chequebookrequestportal.domain.DebitPolicy.NOT_REQUIRED or exists (
                  select charge.id from ChargeTransaction charge
                  where charge.request = request and charge.status = :debitStatus
              ))
            """)
    long countProcessingQueue(@Param("statuses") Collection<RequestStatus> statuses,
            @Param("debitStatus") DebitStatus debitStatus);

    @Query("""
            select request from ChequeRequest request
            where request.status = :status
              and (request.debitPolicy = com.example.chequebookrequestportal.domain.DebitPolicy.NOT_REQUIRED or exists (
                  select charge.id from ChargeTransaction charge
                  where charge.request = request and charge.status = :debitStatus
              ))
              and not exists (
                  select item.id from VendorExportItem item where item.request = request
              )
            order by request.createdAt asc
            """)
    List<ChequeRequest> findExportableByStatus(@Param("status") RequestStatus status,
            @Param("debitStatus") DebitStatus debitStatus);

    @Query("""
            select count(request) from ChequeRequest request
            where request.status = :status
              and (request.debitPolicy = com.example.chequebookrequestportal.domain.DebitPolicy.NOT_REQUIRED or exists (
                  select charge.id from ChargeTransaction charge
                  where charge.request = request and charge.status = :debitStatus
              ))
              and not exists (
                  select item.id from VendorExportItem item where item.request = request
              )
            """)
    long countExportableByStatus(@Param("status") RequestStatus status,
            @Param("debitStatus") DebitStatus debitStatus);

    List<ChequeRequest> findByCreatedAtBetween(Instant from, Instant to);
}
