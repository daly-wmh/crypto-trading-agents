package com.tradingagents.api.dto;

import com.tradingagents.market.CryptoMarketService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record AnalysisRequest(
        @NotBlank @Pattern(regexp = CryptoMarketService.PAIR_PATTERN) String stockCode,
        @Min(20) @Max(120) Integer klineDays,
        List<String> enabledAgents
) {
    public int getKlineDaysOrDefault() {
        return klineDays == null ? 120 : klineDays;
    }
}
