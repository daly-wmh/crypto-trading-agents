package com.tradingagents.core.graph;

import com.tradingagents.api.entity.PipelineNode;
import com.tradingagents.core.node.AgentNode;
import com.tradingagents.core.node.NodeResult;
import com.tradingagents.core.node.NodeType;
import com.tradingagents.core.state.AgentState;
import com.tradingagents.model.ModelClientResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicPipelineAgent implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(DynamicPipelineAgent.class);
    private final PipelineNode config;
    private final ModelClientResolver models;

    public DynamicPipelineAgent(PipelineNode config, ModelClientResolver models) {
        this.config = config;
        this.models = models;
    }

    @Override
    public String getId() { return config.getNodeKey(); }

    @Override
    public String getDisplayName() { return config.getDisplayName(); }

    @Override
    public NodeType getType() {
        return switch (config.getContextType()) {
            case "ANALYST_MARKET", "ANALYST_SOCIAL" -> NodeType.ANALYST;
            case "TRADER" -> NodeType.TRADER;
            default -> NodeType.MANAGER;
        };
    }

    @Override
    public String getModelKey() {
        return models.modelKey(getId(), config.getProviderId(), config.getModelName());
    }

    @Override
    public NodeResult execute(AgentState state) {
        long started = System.currentTimeMillis();
        try {
            String output = models.resolve(getId(), config.getProviderId(), config.getModelName())
                    .prompt()
                    .system(config.getSystemPrompt())
                    .user(buildPrompt(state))
                    .call().content();
            if (output == null || output.isBlank()) {
                throw new IllegalStateException("模型返回空内容");
            }
            storeResult(state, output);
            return NodeResult.success(getId(), output, System.currentTimeMillis() - started, output.length() / 2);
        } catch (Exception e) {
            log.warn("Pipeline node {} failed", getId(), e);
            return NodeResult.failure(getId(), "模型调用失败，请检查系统设置或模型服务", System.currentTimeMillis() - started);
        }
    }

    private String buildPrompt(AgentState state) {
        StringBuilder result = new StringBuilder("现货交易对：").append(state.getStockName()).append("\n\n");
        switch (config.getContextType()) {
            case "ANALYST_MARKET", "ANALYST_SOCIAL" -> {
                append(result, "已收盘 K 线", state.getContext("kline_context"));
                append(result, "技术指标", state.getContext("technical_indicators"));
                append(result, "24 小时行情", state.getContext("ticker_context"));
            }
            default -> {
                append(result, "独立分析", state.getAllAnalystReports());
                append(result, "多空论点", state.getDebateHistory());
                append(result, "研究裁定", state.getResearchDecision());
                append(result, "风控审查", state.getTradeDecision());
                append(result, "24 小时行情", state.getContext("ticker_context"));
                append(result, "历史经验", state.getRecalledMemories());
            }
        }
        result.append("""
                ## 写作要求
                结论先行。每点说明一个判断及其依据或验证条件。删去行情复述、套话和同义反复，同一个价位或事实仅在影响当前判断时引用。重要价位、结论和风险用 Markdown 粗体标出。
                """);
        result.append(switch (config.getContextType()) {
            case "ANALYST_MARKET" -> """
                    先用一两句说明趋势、不同周期是否一致，以及最能说明问题的量价或指标证据。
                    接着用「支撑位」和「阻力位」两组项目列表。每组依次写 **15 分钟**、**1 小时**、**4 小时**、**1 日**，每个周期一条，15 分钟放在同一组的第一条。给出 USDT 价位或区间和可核验的依据，例如已收盘的前高、前低或多次触及。数据不足就标「无法确认」，不要猜测价位。最后用一句写明什么变化会让判断失效。
                    """;
            case "ANALYST_SOCIAL" -> """
                    用 3–4 点说明成交量相对变化、它是否确认价格走势，以及流动性或样本中最值得注意的一项限制。优先解释变化的意义。
                    """;
            case "DEBATE" -> """
                    每轮用 2–3 点说明本方最有力的证据、对对手最强论点的回应，以及可验证的失效条件。后续轮次只补充新证据、反驳或结论变化。没有新证据时直说。
                    """;
            case "DECISION" -> """
                    用 3–4 点给出最可信情景、为何胜过另一种解释、最强反证，以及观察周期和置信度。引用足以支撑裁定的证据。
                    """;
            case "RISK", "TRADER" -> """
                    用 3–4 点说明主要风险和依据、什么条件下可执行或应继续观察、可接受的仓位范围，以及退出或失效条件。
                    """;
            case "CIO" -> """
                    按系统要求保留开头的交易信号。正文用 3–5 点写最终结论、最有分量的证据、触发与退出条件。不逐段复述前面的节点。若列出多周期支撑或阻力，将有依据的 15 分钟价位和其他周期放在同一组。
                    """;
            default -> "用 2–4 点写本节点的结论、最重要的证据及需要验证的条件。\n";
        });
        return result.toString();
    }

    private static void append(StringBuilder prompt, String label, String value) {
        if (value != null && !value.isBlank()) {
            prompt.append("## ").append(label).append("\n").append(value).append("\n\n");
        }
    }

    private void storeResult(AgentState state, String output) {
        switch (config.getContextType()) {
            case "ANALYST_MARKET" -> state.setMarketReport(output);
            case "ANALYST_SOCIAL" -> state.setSocialReport(output);
            case "DEBATE" -> {
                if (getId().startsWith("bear")) state.getBearArguments().add(output);
                else state.getBullArguments().add(output);
            }
            case "DECISION" -> state.setResearchDecision(output);
            case "RISK", "TRADER" -> state.setTradeDecision(output);
            case "CIO" -> state.setFinalDecision(output);
            default -> state.setResearchDecision(output);
        }
    }
}
