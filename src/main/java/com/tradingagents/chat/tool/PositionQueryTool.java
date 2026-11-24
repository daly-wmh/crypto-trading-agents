package com.tradingagents.chat.tool;

import com.tradingagents.trading.dto.PositionVO;
import com.tradingagents.trading.service.TradingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class PositionQueryTool {

    private final TradingService tradingService;

    public PositionQueryTool(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @Tool(description = "查询当前模拟交易的持仓信息，包含持仓交易对、数量、均价、当前价格和盈亏")
    public String queryPositions() {
        try {
            var summary = tradingService.getAccountSummary(1L);
            if (summary.positions() == null || summary.positions().isEmpty()) {
                return "当前无持仓";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("## 当前持仓\n\n");
            sb.append(String.format("总资产: %.2f | 可用资金: %.2f | 总盈亏: %.2f (%.2f%%)\n\n",
                    summary.totalAssets().doubleValue(),
                    summary.available().doubleValue(),
                    summary.totalPnl().doubleValue(),
                    summary.totalPnlRatio().doubleValue()));
            for (PositionVO pos : summary.positions()) {
                sb.append(String.format("- %s: %d股, 均价%.3f, 现价%.3f, 盈亏%.2f%%\n",
                        pos.stockCode(),
                        pos.quantity(),
                        pos.avgCost().doubleValue(),
                        pos.currentPrice().doubleValue(),
                        pos.pnlRatio().doubleValue()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询持仓失败: " + e.getMessage();
        }
    }
}
