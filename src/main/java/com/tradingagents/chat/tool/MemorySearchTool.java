package com.tradingagents.chat.tool;

import com.tradingagents.memory.AgentMemoryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MemorySearchTool {

    private final AgentMemoryService memoryService;

    public MemorySearchTool(AgentMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Tool(description = "搜索历史决策经验和反思记忆。召回该交易对的历史情境和教训，以及其他交易对的可借鉴教训")
    public String searchMemory(@ToolParam(description = "交易对，如 BTCUSDT") String stockCode,
                               @ToolParam(description = "搜索查询，描述你想查找的经验类型，如：追高被套、止损经验、板块轮动") String query) {
        String memories = memoryService.recallRelevantMemories(stockCode, query, 5);
        if (memories == null || memories.isBlank()) {
            return "未找到与 " + stockCode + " 相关的历史记忆";
        }
        return memories;
    }
}
