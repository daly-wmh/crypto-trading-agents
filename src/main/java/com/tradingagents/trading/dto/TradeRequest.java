package com.tradingagents.trading.dto;

import com.tradingagents.market.CryptoMarketService;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TradeRequest(
        @NotNull @Positive Long accountId,
        @NotBlank @Pattern(regexp = CryptoMarketService.PAIR_PATTERN) String stockCode,
        @NotNull @Positive @Digits(integer = 12, fraction = 8) BigDecimal quantity
) {}
