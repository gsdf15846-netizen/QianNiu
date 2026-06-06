package com.qianniu.repository;

import com.qianniu.model.ConversionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversionHistoryRepository extends JpaRepository<ConversionHistory, Long> {
    Optional<ConversionHistory> findByConversionId(String conversionId);
    List<ConversionHistory> findAllByOrderByCreatedAtDesc();
}
