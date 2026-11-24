package com.tradingagents.chat;

import com.tradingagents.chat.tool.MemorySearchTool;
import com.tradingagents.chat.tool.PositionQueryTool;
import com.tradingagents.market.MarketQueryTool;
import com.tradingagents.model.ModelClientResolver;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AdvisorAgent {

    private static final String SYSTEM_PROMPT = """
            你是加密货币现货研究顾问。使用公开行情、技术指标、历史经验和模拟持仓工具辅助分析。
            给出所依据的数据时间、观察周期及风险。区分工具返回的事实与推断。缺少数据时如实说明。
            只讨论 USDT 计价的现货市场。使用中文 Markdown 回复。
            """;

    private final ModelClientResolver models;
    private final MarketQueryTool market;
    private final MemorySearchTool memory;
    private final PositionQueryTool positions;

    public AdvisorAgent(ModelClientResolver models, MarketQueryTool market,
                        MemorySearchTool memory, PositionQueryTool positions) {
        this.models = models;
        this.market = market;
        this.memory = memory;
        this.positions = positions;
    }

    public String chat(List<ChatMessagePair> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        for (ChatMessagePair message : history) {
            if ("user".equals(message.role())) messages.add(new UserMessage(message.content()));
            if ("assistant".equals(message.role())) messages.add(new AssistantMessage(message.content()));
        }
        messages.add(new UserMessage(userMessage));
        return models.resolve("advisor").prompt().messages(messages)
                .tools(market, memory, positions).call().content();
    }

    public record ChatMessagePair(String role, String content) {}
}
