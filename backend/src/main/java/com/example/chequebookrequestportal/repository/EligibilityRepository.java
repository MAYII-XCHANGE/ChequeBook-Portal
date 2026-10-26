package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.EligibilityRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EligibilityRepository extends JpaRepository<EligibilityRecord, Long> {
}
