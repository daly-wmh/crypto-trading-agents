package com.tradingagents.core.event;

import java.time.LocalDateTime;

/**
 * 图执行事件 - 用于SSE实时推送分析进度
 */
public record GraphEvent(
        EventType type,
        String nodeId,
        String nodeName,
        String data,
        LocalDateTime timestamp,
        String stageId
) {
    public GraphEvent(EventType type, String nodeId, String nodeName, String data, LocalDateTime timestamp) {
        this(type, nodeId, nodeName, data, timestamp, null);
    }

    public enum EventType {
        REPORT_CREATED,
        PIPELINE_PLAN,
        GRAPH_START,        // 分析开始
        MEMORY_RECALL,      // 经验记忆召回
        DATA_WARNING,       // 数据警告 (如K线同步失败)
        NODE_START,         // 节点开始执行
        NODE_PROGRESS,      // 节点执行中 (工具调用等)
        NODE_COMPLETE,      // 节点完成
        NODE_ERROR,         // 节点出错
        PHASE_START,        // 阶段开始 (分析阶段/辩论阶段/决策阶段/风控阶段)
        PHASE_COMPLETE,     // 阶段完成
        GRAPH_COMPLETE,     // 全部完成
        GRAPH_ERROR         // 全局错误
    }

    public static GraphEvent memoryRecall(String stockCode, String memories) {
        return new GraphEvent(EventType.MEMORY_RECALL, null, "经验记忆召回", memories, LocalDateTime.now());
    }

    public static GraphEvent warning(String stockCode, String message) {
        return new GraphEvent(EventType.DATA_WARNING, "DATA_SYNC", "数据警告", message, LocalDateTime.now());
    }

    public static GraphEvent nodeStart(String stageId, String nodeId, String nodeName) {
        return new GraphEvent(EventType.NODE_START, nodeId, nodeName, null, LocalDateTime.now(), stageId);
    }

    public static GraphEvent nodeComplete(String stageId, String nodeId, String nodeName, String output) {
        return new GraphEvent(EventType.NODE_COMPLETE, nodeId, nodeName, output, LocalDateTime.now(), stageId);
    }

    public static GraphEvent nodeError(String stageId, String nodeId, String nodeName, String error) {
        return new GraphEvent(EventType.NODE_ERROR, nodeId, nodeName, error, LocalDateTime.now(), stageId);
    }

    public static GraphEvent phaseStart(String stageId, String phase) {
        return new GraphEvent(EventType.PHASE_START, null, phase, null, LocalDateTime.now(), stageId);
    }

    public static GraphEvent phaseComplete(String stageId, String phase) {
        return new GraphEvent(EventType.PHASE_COMPLETE, null, phase, null, LocalDateTime.now(), stageId);
    }

    public static GraphEvent pipelinePlan(String data) {
        return new GraphEvent(EventType.PIPELINE_PLAN, null, "分析计划", data, LocalDateTime.now());
    }

    public static GraphEvent reportCreated(String pair, Long reportId) {
        return new GraphEvent(EventType.REPORT_CREATED, pair, "报告已建立", reportId.toString(), LocalDateTime.now());
    }

    public static GraphEvent graphStart(String stockCode) {
        return new GraphEvent(EventType.GRAPH_START, null, "分析启动", stockCode, LocalDateTime.now());
    }

    public static GraphEvent graphComplete(String summary) {
        return new GraphEvent(EventType.GRAPH_COMPLETE, null, "分析完成", summary, LocalDateTime.now());
    }
}
