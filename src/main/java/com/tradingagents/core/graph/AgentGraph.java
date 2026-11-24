package com.tradingagents.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent编排图 - 对标 LangGraph 的 StateGraph
 * 定义节点的执行顺序和模式
 */
public class AgentGraph {

    private final String name;
    private final List<GraphNode> stages;

    private AgentGraph(String name, List<GraphNode> stages) {
        this.name = name;
        this.stages = Collections.unmodifiableList(stages);
    }

    public String getName() { return name; }
    public List<GraphNode> getStages() { return stages; }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<GraphNode> stages = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder addStage(GraphNode stage) {
            stages.add(stage);
            return this;
        }

        public AgentGraph build() {
            if (stages.isEmpty()) {
                throw new IllegalStateException("Graph must have at least one stage");
            }
            return new AgentGraph(name, stages);
        }
    }
}
