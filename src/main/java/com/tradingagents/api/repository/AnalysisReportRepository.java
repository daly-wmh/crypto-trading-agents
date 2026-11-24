package com.tradingagents.api.repository;

import com.tradingagents.api.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    @Transactional
    @Modifying
    @Query("update AnalysisReport r set r.status = 'RUNNING' where r.id = :id and r.status in ('FAILED', 'INTERRUPTED')")
    int claimForResume(@Param("id") Long id);

    List<AnalysisReport> findTop50ByOrderByCreatedAtDesc();

    List<AnalysisReport> findByStockCodeOrderByCreatedAtDesc(String stockCode);
}
