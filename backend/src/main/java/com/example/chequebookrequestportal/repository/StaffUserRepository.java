package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.StaffUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
    Optional<StaffUser> findByEmailIgnoreCase(String email);

    List<StaffUser> findAllByOrderByFullNameAsc();

    @Query("""
            select distinct user from StaffUser user join user.roles role
            where user.active = true and upper(role.code) = upper(:roleCode)
            order by user.fullName
            """)
    List<StaffUser> findActiveByRole(@Param("roleCode") String roleCode);

    @Query("""
            select distinct user from StaffUser user join user.roles role join user.branch branch
            where user.active = true
              and upper(role.code) = upper(:roleCode)
              and upper(branch.code) = upper(:branchCode)
              and branch.active = true
            order by user.fullName
            """)
    List<StaffUser> findActiveByRoleAndBranch(@Param("roleCode") String roleCode,
            @Param("branchCode") String branchCode);

    @Query("""
            select distinct user from StaffUser user join user.roles role join user.branch branch
            where user.active = true
              and upper(role.code) = upper(:roleCode)
              and upper(branch.code) = upper(:branchCode)
            order by user.fullName
            """)
    List<StaffUser> findActiveByRoleAndBranchIncludingInactiveBranch(@Param("roleCode") String roleCode,
            @Param("branchCode") String branchCode);
}
