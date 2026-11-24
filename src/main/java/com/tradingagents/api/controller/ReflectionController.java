package com.tradingagents.api.controller;

import com.tradingagents.api.entity.AnalysisReport;
import com.tradingagents.api.repository.AnalysisReportRepository;
import com.tradingagents.memory.AgentMemoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 反思学习API - 用户标记决策结果，系统存储经验教训
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "pgvector")
@RestController
@RequestMapping("/api/reports")
public class ReflectionController {

    private final AnalysisReportRepository reportRepository;
    private final AgentMemoryService memoryService;

    public ReflectionController(AnalysisReportRepository reportRepository,
                                AgentMemoryService memoryService) {
        this.reportRepository = reportRepository;
        this.memoryService = memoryService;
    }

    /**
     * 提交反思评估
     * body: { "outcome": "正确/错误/部分正确", "lesson": "经验教训..." }
     */
    @PostMapping("/{id}/reflect")
    public ResponseEntity<Map<String, String>> reflect(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        AnalysisReport report = reportRepository.findById(id).orElse(null);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }

        String outcome = body.getOrDefault("outcome", "未评估");
        String lesson = body.getOrDefault("lesson", "");

        // 存入向量库作为反思记忆
        String decision = report.getFinalDecision() != null
                ? report.getFinalDecision().substring(0, Math.min(300, report.getFinalDecision().length()))
                : "无决策记录";

        memoryService.reflect(report.getStockCode(), decision, outcome, lesson);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "反思已存储，下次分析该交易对时将召回此经验"
        ));
    }
}
