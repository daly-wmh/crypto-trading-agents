package com.tradingagents.trading.dto;

import com.tradingagents.trading.entity.TradePosition;

import java.math.BigDecimal;

/**
 * 持仓视图对象 (含实时盈亏)
 */
public record PositionVO(
        Long id,
        String stockCode,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal pnlRatio
) {
    public static PositionVO from(TradePosition pos, BigDecimal currentPrice) {
        return new PositionVO(
                pos.getId(),
                pos.getStockCode(),
                pos.getQuantity(),
                pos.getAvgCost(),
                currentPrice,
                pos.marketValue(currentPrice),
                pos.unrealizedPnl(currentPrice),
                pos.pnlRatio(currentPrice)
        );
    }
}
