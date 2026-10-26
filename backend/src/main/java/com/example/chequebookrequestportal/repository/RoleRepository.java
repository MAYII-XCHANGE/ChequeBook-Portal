package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.PortalRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<PortalRole, Long> {
    Optional<PortalRole> findByCodeIgnoreCase(String code);
}
