package com.tradingagents.api.controller;

import com.tradingagents.memory.AgentMemoryService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 历史经验记忆管理 - 增删查
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "pgvector")
@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final JdbcTemplate jdbc;
    private final VectorStore vectorStore;
    private final AgentMemoryService memoryService;

    public MemoryController(JdbcTemplate jdbc, VectorStore vectorStore, AgentMemoryService memoryService) {
        this.jdbc = jdbc;
        this.vectorStore = vectorStore;
        this.memoryService = memoryService;
    }

    /** 查询所有记忆（分页） */
    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(defaultValue = "") String stockCode,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "50") int limit) {

        StringBuilder sql = new StringBuilder("SELECT id, content, metadata, embedding IS NOT NULL as has_embedding FROM vector_store WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!stockCode.isBlank()) {
            sql.append(" AND metadata->>'stock_code' = ?");
            params.add(stockCode);
        }
        if (!type.isBlank()) {
            sql.append(" AND metadata->>'type' = ?");
            params.add(type);
        }
        sql.append(" ORDER BY metadata->>'stock_code' LIMIT ?");
        params.add(limit);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /** 按语义搜索记忆 */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String query,
                                            @RequestParam(defaultValue = "10") int topK) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).similarityThreshold(0.4).build());

        if (results == null) return List.of();
        return results.stream().map(d -> {
            Map<String, Object> map = new HashMap<>(d.getMetadata());
            map.put("id", d.getId());
            map.put("content", d.getText());
            return map;
        }).toList();
    }

    /** 手动添加经验教训 */
    @PostMapping
    public ResponseEntity<Map<String, String>> add(@RequestBody Map<String, String> body) {
        String stockCode = body.getOrDefault("stockCode", "GENERAL");
        String content = body.get("content");
        String type = body.getOrDefault("type", "reflection_memory");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content 不能为空"));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stock_code", stockCode);
        metadata.put("type", type);
        if (body.containsKey("direction")) {
            metadata.put("decision_direction", body.get("direction"));
        }

        vectorStore.add(List.of(new Document(content, metadata)));
        return ResponseEntity.ok(Map.of("message", "经验已添加"));
    }

    /** 删除记忆 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String id) {
        int rows = jdbc.update("DELETE FROM vector_store WHERE id = ?", UUID.fromString(id));
        if (rows > 0) {
            return ResponseEntity.ok(Map.of("message", "已删除"));
        }
        return ResponseEntity.notFound().build();
    }

    /** 按交易对代码批量删除 */
    @DeleteMapping("/by-stock/{stockCode}")
    public ResponseEntity<Map<String, String>> deleteByStock(@PathVariable String stockCode) {
        int rows = jdbc.update("DELETE FROM vector_store WHERE metadata->>'stock_code' = ?", stockCode);
        return ResponseEntity.ok(Map.of("message", "已删除 " + rows + " 条记忆"));
    }

    /** 统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> result = new HashMap<>();
        result.put("total", jdbc.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class));
        result.put("situations", jdbc.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'type' = 'situation_memory'", Integer.class));
        result.put("reflections", jdbc.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'type' = 'reflection_memory'", Integer.class));
        result.put("stocks", jdbc.queryForList(
                "SELECT DISTINCT metadata->>'stock_code' as code FROM vector_store ORDER BY code"));
        return result;
    }
}
