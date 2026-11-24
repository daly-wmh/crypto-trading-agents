package com.tradingagents.core.graph;

import com.tradingagents.api.entity.PipelineNode;
import com.tradingagents.api.repository.PipelineNodeRepository;
import com.tradingagents.model.ModelClientResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 流水线服务 - 从 pipeline_node 表动态构建 AgentGraph
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final PipelineNodeRepository nodeRepository;
    private final ModelClientResolver modelClientResolver;

    public PipelineService(PipelineNodeRepository nodeRepository,
                           ModelClientResolver modelClientResolver) {
        this.nodeRepository = nodeRepository;
        this.modelClientResolver = modelClientResolver;
    }

    /**
     * 从数据库构建分析图 (每次分析调用时重新读取，支持热更新)
     */
    public AgentGraph buildGraph() {
        List<PipelineNode> nodes = nodeRepository.findByEnabledTrueOrderByStageOrderAscPositionInStageAsc();

        if (nodes.isEmpty()) {
            throw new IllegalStateException("流水线配置为空，请在系统设置中配置分析节点");
        }

        // 按 stageOrder 分组
        Map<Integer, List<PipelineNode>> stages = nodes.stream()
                .collect(Collectors.groupingBy(PipelineNode::getStageOrder, LinkedHashMap::new, Collectors.toList()));

        var builder = AgentGraph.builder("Dynamic Analysis Pipeline");

        for (var entry : stages.entrySet()) {
            List<PipelineNode> stageNodes = entry.getValue();
            List<DynamicPipelineAgent> agents = stageNodes.stream()
                    .map(this::createAgent)
                    .toList();

            String stageId = "stage_" + entry.getKey();
            String stageName = stageNodes.size() == 1
                    ? stageNodes.get(0).getDisplayName()
                    : stageNodes.get(0).getDisplayName() + " 等" + stageNodes.size() + "个";

            // 判断阶段类型
            String stageType = stageNodes.get(0).getStageType();
            if ("DEBATE".equalsIgnoreCase(stageType) && agents.size() >= 2) {
                int rounds = stageNodes.get(0).getDebateRounds() != null ? stageNodes.get(0).getDebateRounds() : 2;
                builder.addStage(GraphNode.debate(stageId, stageName, new ArrayList<>(agents), rounds));
            } else if (agents.size() == 1) {
                builder.addStage(GraphNode.single(stageId, stageName, agents.get(0)));
            } else {
                builder.addStage(GraphNode.parallel(stageId, stageName, new ArrayList<>(agents)));
            }
        }

        log.info("Built dynamic pipeline with {} stages, {} nodes", stages.size(), nodes.size());
        return builder.build();
    }

    private DynamicPipelineAgent createAgent(PipelineNode node) {
        return new DynamicPipelineAgent(node, modelClientResolver);
    }

    /**
     * 获取所有节点 (含禁用的)
     */
    public List<PipelineNode> getAllNodes() {
        return nodeRepository.findAllByOrderByStageOrderAscPositionInStageAsc();
    }

    public PipelineNode save(PipelineNode node) {
        return nodeRepository.save(node);
    }

    public void delete(Long id) {
        nodeRepository.deleteById(id);
    }

    public Optional<PipelineNode> getById(Long id) {
        return nodeRepository.findById(id);
    }

    /**
     * 批量更新排序
     */
    @org.springframework.transaction.annotation.Transactional
    public void updateOrder(List<Map<String, Object>> orderList) {
        for (Map<String, Object> item : orderList) {
            Long id = Long.valueOf(item.get("id").toString());
            int stageOrder = Integer.parseInt(item.get("stageOrder").toString());
            int position = Integer.parseInt(item.get("positionInStage").toString());
            nodeRepository.findById(id).ifPresent(node -> {
                node.setStageOrder(stageOrder);
                node.setPositionInStage(position);
                nodeRepository.save(node);
            });
        }
    }
}
