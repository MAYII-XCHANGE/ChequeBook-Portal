package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.Branch;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select branch from Branch branch where lower(branch.code) = lower(:code)")
    Optional<Branch> findByCodeForUpdate(@Param("code") String code);

    Optional<Branch> findByManagersChequeAccount(String managersChequeAccount);

    @Query("""
            select branch from Branch branch
            where lower(branch.chargeGlAccount) = lower(:account)
               or lower(branch.vatGlAccount) = lower(:account)
            """)
    List<Branch> findUsingGlAccount(@Param("account") String account);
}
