package com.tradingagents.api.controller;

import com.tradingagents.api.entity.AnalysisReport;
import com.tradingagents.api.repository.AnalysisReportRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 历史报告查询API
 */
@RestController
@RequestMapping("/api/reports")
public class ReportListController {

    private final AnalysisReportRepository reportRepository;

    public ReportListController(AnalysisReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * 获取最近50条分析报告
     */
    @GetMapping
    public List<AnalysisReport> listReports() {
        return reportRepository.findTop50ByOrderByCreatedAtDesc();
    }

    /**
     * 按交易对代码查询
     */
    @GetMapping("/stock/{code}")
    public List<AnalysisReport> listByStock(@PathVariable String code) {
        return reportRepository.findByStockCodeOrderByCreatedAtDesc(code);
    }

    /**
     * 获取单条报告详情
     */
    @GetMapping("/{id}")
    public AnalysisReport getReport(@PathVariable Long id) {
        return reportRepository.findById(id).orElse(null);
    }

    /**
     * 删除单条报告
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        if (!reportRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        reportRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量删除报告
     */
    @DeleteMapping("/batch")
    public ResponseEntity<Void> deleteReports(@RequestBody List<Long> ids) {
        reportRepository.deleteAllById(ids);
        return ResponseEntity.noContent().build();
    }
}
