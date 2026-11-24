package com.tradingagents.api.controller;

import com.tradingagents.api.entity.PipelineNode;
import com.tradingagents.core.graph.PipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    private final PipelineService pipelineService;

    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /** 获取所有节点（含禁用） */
    @GetMapping
    public List<PipelineNode> getAll() {
        return pipelineService.getAllNodes();
    }

    /** 新增节点 */
    @PostMapping
    public PipelineNode create(@RequestBody PipelineNode node) {
        node.setId(null);
        return pipelineService.save(node);
    }

    /** 更新节点（含 systemPrompt 编辑） */
    @PutMapping("/{id}")
    public ResponseEntity<PipelineNode> update(@PathVariable Long id, @RequestBody PipelineNode patch) {
        return pipelineService.getById(id)
                .map(node -> {
                    if (patch.getDisplayName() != null) node.setDisplayName(patch.getDisplayName());
                    if (patch.getSystemPrompt() != null) node.setSystemPrompt(patch.getSystemPrompt());
                    if (patch.getContextType() != null) node.setContextType(patch.getContextType());
                    if (patch.getStageOrder() != null) node.setStageOrder(patch.getStageOrder());
                    if (patch.getPositionInStage() != null) node.setPositionInStage(patch.getPositionInStage());
                    if (patch.getProviderId() != null) node.setProviderId(patch.getProviderId());
                    if (patch.getModelName() != null) node.setModelName(patch.getModelName());
                    if (patch.getEnabled() != null) node.setEnabled(patch.getEnabled());
                    if (patch.getStageType() != null) node.setStageType(patch.getStageType());
                    if (patch.getDebateRounds() != null) node.setDebateRounds(patch.getDebateRounds());
                    return ResponseEntity.ok(pipelineService.save(node));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 删除节点 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        pipelineService.delete(id);
        return ResponseEntity.ok().build();
    }

    /** 批量更新排序 */
    @PutMapping("/order")
    public ResponseEntity<Void> updateOrder(@RequestBody List<Map<String, Object>> orderList) {
        pipelineService.updateOrder(orderList);
        return ResponseEntity.ok().build();
    }

    /** 切换启停 */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<PipelineNode> toggle(@PathVariable Long id) {
        return pipelineService.getById(id)
                .map(node -> {
                    node.setEnabled(!node.getEnabled());
                    return ResponseEntity.ok(pipelineService.save(node));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
