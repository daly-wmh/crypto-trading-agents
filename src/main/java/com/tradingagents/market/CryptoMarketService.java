package com.tradingagents.market;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class CryptoMarketService {

    public static final String PAIR_PATTERN = "(?i)^[A-Z0-9]{2,12}USDT$";
    private static final int MAX_CANDLES = 240;
    private static final List<String> CHART_INTERVALS = List.of("15m", "1h", "4h", "1d");
    private static final Duration CANDLE_CACHE_TTL = Duration.ofSeconds(5);

    private final RestClient client;
    private final List<String> pairs;
    private final ConcurrentHashMap<String, Cached<Ticker>> tickers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CandleKey, Cached<List<Candle>>> candles = new ConcurrentHashMap<>();

    public CryptoMarketService(
            @Value("${crypto.market.base-url}") String baseUrl,
            @Value("${crypto.market.pairs}") String configuredPairs) {
        pairs = Arrays.stream(configuredPairs.split(","))
                .map(String::strip)
                .map(pair -> pair.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (pairs.isEmpty() || pairs.stream().anyMatch(pair -> !pair.matches(PAIR_PATTERN))) {
            throw new IllegalArgumentException("crypto.market.pairs must contain USDT spot pairs");
        }

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    public List<Instrument> instruments() {
        return pairs.stream().map(code -> new Instrument(code, displayName(code))).toList();
    }

    public String requirePair(String code) {
        String pair = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!pairs.contains(pair)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持该交易对");
        }
        return pair;
    }

    public String displayName(String pair) {
        return pair.substring(0, pair.length() - 4) + "/USDT";
    }

    public Ticker ticker(String input) {
        String pair = requirePair(input);
        return cached(tickers, pair, Duration.ofSeconds(5), () -> {
            JsonNode data = client.get().uri("/api/v3/ticker/24hr?symbol={pair}", pair)
                    .retrieve().body(JsonNode.class);
            return new Ticker(pair, number(data, "lastPrice"), number(data, "priceChangePercent"),
                    number(data, "highPrice"), number(data, "lowPrice"), number(data, "quoteVolume"),
                    Instant.ofEpochMilli(data.required("closeTime").asLong()));
        });
    }

    public List<Candle> candles(String input, String interval, int count) {
        String pair = requirePair(input);
        if (!CHART_INTERVALS.contains(interval) || count < 1 || count > MAX_CANDLES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "周期须为 15m、1h、4h 或 1d，K 线条数须在 1 至 240 之间");
        }

        CandleKey key = new CandleKey(pair, interval);
        List<Candle> history = cached(candles, key, CANDLE_CACHE_TTL, () -> loadCandles(key));
        return history.subList(Math.max(0, history.size() - count), history.size());
    }

    private List<Candle> loadCandles(CandleKey key) {
        JsonNode data = client.get().uri("/api/v3/klines?symbol={pair}&interval={interval}&limit={limit}",
                        key.pair(), key.interval(), MAX_CANDLES)
                .retrieve().body(JsonNode.class);
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new IllegalArgumentException("Missing market candles");
        }

        List<Candle> result = new ArrayList<>(data.size());
        for (JsonNode row : data) {
            result.add(new Candle(Instant.ofEpochMilli(row.required(0).asLong()),
                    number(row.required(1)), number(row.required(2)), number(row.required(3)),
                    number(row.required(4)), number(row.required(5)),
                    Instant.ofEpochMilli(row.required(6).asLong())));
        }
        return List.copyOf(result);
    }

    public String buildMultiTimeframeContext(String code, int count) {
        StringBuilder result = new StringBuilder("Binance 现货，报价 USDT，时间 UTC。以下仅含已收盘 K 线。\n");
        for (String interval : CHART_INTERVALS) {
            List<Candle> history = closedCandles(code, interval, Math.clamp(count, 20, 120));
            result.append("\n").append(interval).append(" 最近 ").append(history.size()).append(" 根\n");
            if (history.isEmpty()) continue;
            List<Candle> recent = history.subList(Math.max(0, history.size() - 8), history.size());
            for (Candle candle : recent) {
                result.append("%s 开 %s 高 %s 低 %s 收 %s 量 %s%n".formatted(
                        candle.openTime(), candle.open(), candle.high(), candle.low(), candle.close(), candle.volume()));
            }
        }
        return result.toString();
    }

    public String buildIndicatorSummary(String code, int count) {
        StringBuilder result = new StringBuilder();
        for (String interval : CHART_INTERVALS) {
            List<Candle> history = closedCandles(code, interval, Math.clamp(count, 51, MAX_CANDLES));
            List<BigDecimal> closes = history.stream().map(Candle::close).toList();
            result.append("%s · SMA20 %s · SMA50 %s · RSI14 %s%n".formatted(interval,
                    average(closes, 20), average(closes, 50), rsi(closes)));
        }
        return result.toString();
    }

    private List<Candle> closedCandles(String code, String interval, int count) {
        Instant now = Instant.now();
        return candles(code, interval, count).stream().filter(candle -> candle.closeTime().isBefore(now)).toList();
    }

    private static String average(List<BigDecimal> values, int count) {
        if (values.size() < count) return "样本不足";
        BigDecimal sum = values.subList(values.size() - count, values.size()).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static String rsi(List<BigDecimal> values) {
        if (values.size() < 15) return "样本不足";
        double gain = 0;
        double loss = 0;
        for (int i = 1; i < values.size(); i++) {
            double change = values.get(i).subtract(values.get(i - 1)).doubleValue();
            if (i <= 14) {
                gain += Math.max(0, change) / 14;
                loss += Math.max(0, -change) / 14;
            } else {
                gain = (gain * 13 + Math.max(0, change)) / 14;
                loss = (loss * 13 + Math.max(0, -change)) / 14;
            }
        }
        double value = gain == 0 && loss == 0 ? 50 : 100 * gain / (gain + loss);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static <K, T> T cached(ConcurrentHashMap<K, Cached<T>> cache, K key, Duration ttl, Supplier<T> load) {
        try {
            return cache.compute(key, (ignored, current) -> {
                if (current != null && current.expiresAt().isAfter(Instant.now())) return current;
                return new Cached<>(load.get(), Instant.now().plus(ttl));
            }).value();
        } catch (RestClientException | IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "公开行情暂不可用，请稍后重试", e);
        }
    }

    private static BigDecimal number(JsonNode node, String name) {
        return number(node.required(name));
    }

    private static BigDecimal number(JsonNode node) {
        return new BigDecimal(node.asText());
    }

    public record Instrument(String code, String name) {}
    public record Ticker(String code, BigDecimal price, BigDecimal changePercent, BigDecimal high,
                         BigDecimal low, BigDecimal quoteVolume, Instant observedAt) {}
    public record Candle(Instant openTime, BigDecimal open, BigDecimal high, BigDecimal low,
                         BigDecimal close, BigDecimal volume, Instant closeTime) {}

    private record CandleKey(String pair, String interval) {}
    private record Cached<T>(T value, Instant expiresAt) {}
}
