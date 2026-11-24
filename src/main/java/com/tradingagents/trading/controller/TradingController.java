package com.tradingagents.trading.controller;

import com.tradingagents.trading.dto.AccountSummary;
import com.tradingagents.trading.dto.PaperSignal.Action;
import com.tradingagents.trading.dto.TradeRequest;
import com.tradingagents.trading.entity.TradeOrder;
import com.tradingagents.trading.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trading")
public class TradingController {

    private final TradingService trading;

    public TradingController(TradingService trading) {
        this.trading = trading;
    }

    @GetMapping("/account/{accountId}")
    public AccountSummary account(@PathVariable Long accountId) {
        return trading.getAccountSummary(accountId);
    }

    @GetMapping("/orders/{accountId}")
    public List<TradeOrder> orders(@PathVariable Long accountId) {
        return trading.getOrderHistory(accountId);
    }

    @GetMapping("/price/{pair}")
    public Map<String, Object> price(@PathVariable String pair) {
        return Map.of("price", trading.getLatestPrice(pair));
    }

    @PostMapping("/buy")
    public TradeOrder buy(@Valid @RequestBody TradeRequest request) {
        return trading.trade(request, Action.BUY);
    }

    @PostMapping("/sell")
    public TradeOrder sell(@Valid @RequestBody TradeRequest request) {
        return trading.trade(request, Action.SELL);
    }

    @PostMapping("/execute-decision/{reportId}")
    public TradeOrder executeDecision(@PathVariable Long reportId, @RequestParam(defaultValue = "1") Long accountId) {
        return trading.executeReport(accountId, reportId);
    }
}
