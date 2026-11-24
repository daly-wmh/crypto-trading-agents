package com.tradingagents.core.node;

import com.tradingagents.core.state.AgentState;

/**
 * Agent节点基础接口
 * 对标 LangGraph 中的 Node 概念
 */
public interface AgentNode {

    /**
     * 节点唯一标识
     */
    String getId();

    /**
     * 节点显示名称 (用于SSE推送)
     */
    String getDisplayName();

    /**
     * 节点类型: ANALYST / RESEARCHER / MANAGER / TRADER / RISK
     */
    NodeType getType();

    default String getModelKey() { return "default"; }

    /**
     * 执行节点逻辑
     * @param state 全局共享状态
     * @return 执行结果
     */
    NodeResult execute(AgentState state);
}
