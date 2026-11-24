package com.tradingagents.core.graph;

import com.tradingagents.core.event.GraphEvent;
import com.tradingagents.core.node.AgentNode;
import com.tradingagents.core.node.NodeResult;
import com.tradingagents.core.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 图执行器 - 对标 LangGraph 的 Graph.invoke()
 * 按阶段顺序执行，支持并行和辩论循环
 * 并行策略: 同模型串行(避免Ollama排队)，不同模型并行
 */
public class GraphExecutor {

    private static final Logger log = LoggerFactory.getLogger(GraphExecutor.class);

    private final AgentGraph graph;
    private final ExecutorService parallelExecutor;
    private final Consumer<GraphEvent> eventListener;

    public GraphExecutor(AgentGraph graph, Consumer<GraphEvent> eventListener) {
        this.graph = graph;
        this.eventListener = eventListener;
        this.parallelExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 执行整个图
     */
    public AgentState execute(AgentState state) {
        return execute(state, 0, null);
    }

    /**
     * 从指定阶段开始执行 (断点续连)
     * @param startFromStage 从第几个阶段开始 (0-based)
     * @param stageCompleteListener 每阶段完成后的回调 (stageIndex, stageId)
     */
    public AgentState execute(AgentState state, int startFromStage,
                              java.util.function.BiConsumer<Integer, String> stageCompleteListener) {
        emit(GraphEvent.graphStart(state.getStockCode()));
        long startTime = System.currentTimeMillis();

        try {
            List<GraphNode> stages = graph.getStages();
            for (int i = 0; i < stages.size(); i++) {
                if (i < startFromStage) {
                    log.info("Skipping stage {} ({}) - already completed", i, stages.get(i).getId());
                    continue;
                }
                GraphNode stage = stages.get(i);
                executeStage(stage, state);
                if (stageCompleteListener != null) {
                    stageCompleteListener.accept(i, stage.getId());
                }
                emit(GraphEvent.phaseComplete(stage.getId(), stage.getPhaseName()));
            }
            long totalMs = System.currentTimeMillis() - startTime;
            emit(GraphEvent.graphComplete("总耗时: " + totalMs + "ms"));
        } catch (Exception e) {
            log.error("Graph execution failed for stock: {}", state.getStockCode(), e);
            throw new GraphExecutionException("Graph execution failed: " + e.getMessage(), e);
        } finally {
            parallelExecutor.shutdown();
        }

        return state;
    }

    private void executeStage(GraphNode stage, AgentState state) {
        emit(GraphEvent.phaseStart(stage.getId(), stage.getPhaseName()));

        switch (stage.getMode()) {
            case SINGLE -> executeSingle(stage, state);
            case PARALLEL -> executeParallel(stage, state);
            case SEQUENTIAL -> executeSequential(stage, state);
            case DEBATE -> executeDebate(stage, state);
        }

    }

    /**
     * 单节点执行
     */
    private void executeSingle(GraphNode stage, AgentState state) {
        AgentNode node = stage.getNodes().get(0);
        executeNode(stage, node, state, node.getDisplayName());
    }

    /**
     * 智能并行 - 按模型分组: 同模型串行(避免Ollama排队)，不同模型并行
     */
    private void executeParallel(GraphNode stage, AgentState state) {
        // 按模型分组
        Map<String, List<AgentNode>> modelGroups = new LinkedHashMap<>();
        for (AgentNode node : stage.getNodes()) {
            String modelKey = node.getModelKey();
            modelGroups.computeIfAbsent(modelKey, k -> new ArrayList<>()).add(node);
        }

        if (modelGroups.size() == 1) {
            // 所有agent用同一模型 → 纯串行
            log.info("All agents use same model [{}], executing sequentially", modelGroups.keySet().iterator().next());
            for (AgentNode node : stage.getNodes()) {
                executeNode(stage, node, state, node.getDisplayName());
            }
        } else {
            // 不同模型 → 组间并行，组内串行
            log.info("Agents use {} different models, executing groups in parallel", modelGroups.size());
            List<CompletableFuture<Void>> groupFutures = new ArrayList<>();

            for (Map.Entry<String, List<AgentNode>> entry : modelGroups.entrySet()) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (AgentNode node : entry.getValue()) {
                        executeNode(stage, node, state, node.getDisplayName());
                    }
                }, parallelExecutor);
                groupFutures.add(future);
            }

            CompletableFuture.allOf(groupFutures.toArray(new CompletableFuture[0])).join();
        }
    }

    /**
     * 顺序执行 - 逐个执行节点 (避免Ollama并行排队超时)
     */
    private void executeSequential(GraphNode stage, AgentState state) {
        for (AgentNode node : stage.getNodes()) {
            executeNode(stage, node, state, node.getDisplayName());
        }
    }

    /**
     * 辩论执行 - 多轮循环
     * 节点按顺序轮流发言，每轮每个节点发言一次
     */
    private void executeDebate(GraphNode stage, AgentState state) {
        int maxRounds = stage.getMaxRounds();
        List<AgentNode> debaters = stage.getNodes();

        for (int round = 1; round <= maxRounds; round++) {
            log.info("Debate [{}] round {}/{}", stage.getId(), round, maxRounds);
            for (AgentNode node : debaters) {
                String label = node.getDisplayName() + " (第" + round + "轮)";
                executeNode(stage, node, state, label);
            }
        }
    }

    private void executeNode(GraphNode stage, AgentNode node, AgentState state, String label) {
        emit(GraphEvent.nodeStart(stage.getId(), node.getId(), label));
        NodeResult result = doExecuteNode(node, state);
        if (result.success()) {
            emit(GraphEvent.nodeComplete(stage.getId(), node.getId(), label, result.output()));
        } else {
            emit(GraphEvent.nodeError(stage.getId(), node.getId(), label, result.errorMessage()));
            throw new IllegalStateException(label + "执行失败");
        }
    }

    private NodeResult doExecuteNode(AgentNode node, AgentState state) {
        // 检查agent是否被启用
        if (!state.isAgentEnabled(node.getId())) {
            log.info("Node [{}] skipped (disabled by user)", node.getId());
            return NodeResult.success(node.getId(), "[已跳过]", 0, 0);
        }

        long start = System.currentTimeMillis();
        try {
            NodeResult result = node.execute(state);
            state.recordTiming(node.getId(), result.durationMs());
            state.addTokens(result.tokensUsed());
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Node [{}] execution failed", node.getId(), e);
            state.recordTiming(node.getId(), duration);
            return NodeResult.failure(node.getId(), e.getMessage(), duration);
        }
    }

    private void emit(GraphEvent event) {
        if (eventListener != null) {
            eventListener.accept(event);
        }
    }

    public static class GraphExecutionException extends RuntimeException {
        public GraphExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
