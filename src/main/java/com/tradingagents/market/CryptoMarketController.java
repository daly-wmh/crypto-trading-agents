package com.tradingagents.market;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CryptoMarketController {

    private final CryptoMarketService market;

    public CryptoMarketController(CryptoMarketService market) {
        this.market = market;
    }

    @GetMapping("/market/pairs")
    public List<CryptoMarketService.Instrument> pairs() {
        return market.instruments();
    }

    @GetMapping("/market/ticker/{pair}")
    public CryptoMarketService.Ticker ticker(@PathVariable String pair) {
        return market.ticker(pair);
    }

    @GetMapping("/kline/{pair}")
    public Map<String, Object> chart(@PathVariable String pair,
                                     @RequestParam(defaultValue = "120") int days,
                                     @RequestParam(defaultValue = "1h") String period) {
        var candles = market.candles(pair, period, days);
        return Map.of(
                "dates", candles.stream().map(CryptoMarketService.Candle::openTime).toList(),
                "ohlc", candles.stream().map(c -> List.of(c.open(), c.close(), c.low(), c.high())).toList(),
                "volumes", candles.stream().map(CryptoMarketService.Candle::volume).toList());
    }
}
