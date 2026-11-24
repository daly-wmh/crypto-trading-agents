package com.tradingagents.core.graph;

import com.tradingagents.core.node.AgentNode;

import java.util.List;

/**
 * 图节点包装 - 定义节点在图中的执行模式
 */
public class GraphNode {

    public enum ExecutionMode {
        SINGLE,     // 单节点执行
        PARALLEL,   // 多节点并行执行
        SEQUENTIAL, // 多节点顺序执行 (避免Ollama排队超时)
        DEBATE      // 对抗辩论 (多轮循环)
    }

    private final String id;
    private final String phaseName;
    private final ExecutionMode mode;
    private final List<AgentNode> nodes;
    private final int maxRounds;  // 仅DEBATE模式使用

    private GraphNode(String id, String phaseName, ExecutionMode mode, List<AgentNode> nodes, int maxRounds) {
        this.id = id;
        this.phaseName = phaseName;
        this.mode = mode;
        this.nodes = nodes;
        this.maxRounds = maxRounds;
    }

    public static GraphNode single(String id, String phaseName, AgentNode node) {
        return new GraphNode(id, phaseName, ExecutionMode.SINGLE, List.of(node), 1);
    }

    public static GraphNode parallel(String id, String phaseName, List<AgentNode> nodes) {
        return new GraphNode(id, phaseName, ExecutionMode.PARALLEL, nodes, 1);
    }

    public static GraphNode sequential(String id, String phaseName, List<AgentNode> nodes) {
        return new GraphNode(id, phaseName, ExecutionMode.SEQUENTIAL, nodes, 1);
    }

    public static GraphNode debate(String id, String phaseName, List<AgentNode> nodes, int maxRounds) {
        return new GraphNode(id, phaseName, ExecutionMode.DEBATE, nodes, maxRounds);
    }

    public String getId() { return id; }
    public String getPhaseName() { return phaseName; }
    public ExecutionMode getMode() { return mode; }
    public List<AgentNode> getNodes() { return nodes; }
    public int getMaxRounds() { return maxRounds; }
}
