package com.example.chequebookrequestportal.repository;

import com.example.chequebookrequestportal.domain.RequestHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestHistoryRepository extends JpaRepository<RequestHistory, Long> {
    List<RequestHistory> findByRequestRequestNumberOrderByCreatedAtAsc(String requestNumber);
}
