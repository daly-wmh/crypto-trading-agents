package com.tradingagents.core.node;

/**
 * 节点类型枚举
 */
public enum NodeType {
    ANALYST,      // 分析师 (技术/消息/舆情/基本面)
    RESEARCHER,   // 研究员 (多头/空头)
    MANAGER,      // 管理者 (研究主管/风控主管)
    TRADER,       // 交易员
    RISK          // 风控辩手 (激进/保守/中性)
}
