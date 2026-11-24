package com.tradingagents.trading.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户总览
 */
public record AccountSummary(
        Long accountId,
        String accountName,
        BigDecimal balance,
        BigDecimal frozen,
        BigDecimal available,
        BigDecimal totalMarketValue,
        BigDecimal totalAssets,
        BigDecimal totalPnl,
        BigDecimal totalPnlRatio,
        List<PositionVO> positions
) {}
