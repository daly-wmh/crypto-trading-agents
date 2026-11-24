package com.tradingagents.market;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MarketQueryTool {

    private final CryptoMarketService market;

    public MarketQueryTool(CryptoMarketService market) {
        this.market = market;
    }

    @Tool(description = "查询 Binance 现货交易对的 24 小时价格、涨跌幅和成交额。")
    public CryptoMarketService.Ticker ticker(@ToolParam(description = "交易对，如 BTCUSDT") String pair) {
        return market.ticker(pair);
    }

    @Tool(description = "查询现货交易对的 1h、4h、1d 已收盘 K 线与技术指标。")
    public String candles(@ToolParam(description = "交易对，如 ETHUSDT") String pair) {
        return market.buildMultiTimeframeContext(pair, 51) + "\n" + market.buildIndicatorSummary(pair, 120);
    }
}
