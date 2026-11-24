package com.tradingagents.core.node;

/**
 * 节点执行结果
 */
public record NodeResult(
        String nodeId,
        boolean success,
        String output,
        String errorMessage,
        long durationMs,
        int tokensUsed
) {
    public static NodeResult success(String nodeId, String output, long durationMs, int tokens) {
        return new NodeResult(nodeId, true, output, null, durationMs, tokens);
    }

    public static NodeResult failure(String nodeId, String error, long durationMs) {
        return new NodeResult(nodeId, false, null, error, durationMs, 0);
    }
}
