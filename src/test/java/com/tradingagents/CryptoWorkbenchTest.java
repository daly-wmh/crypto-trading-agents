package com.tradingagents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:crypto-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.ai.vectorstore.type=none"
})
class CryptoWorkbenchTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicBoolean FAIL_FINAL_STEP = new AtomicBoolean();
    private static final AtomicReference<String> TECHNICAL_PROMPT = new AtomicReference<>();
    private static final AtomicReference<String> DEBATE_PROMPT = new AtomicReference<>();
    private static final AtomicReference<String> FINAL_PROMPT = new AtomicReference<>();
    private static final AtomicReference<String> NEWS_PROMPT = new AtomicReference<>();
    private static final AtomicReference<JsonNode> NEWS_FORMAT = new AtomicReference<>();
    private static final AtomicInteger NEWS_MODEL_CALLS = new AtomicInteger();
    private static final HttpServer UPSTREAM = upstream();

    @Autowired
    TestRestTemplate client;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void upstreamProperties(DynamicPropertyRegistry properties) {
        String url = "http://127.0.0.1:" + UPSTREAM.getAddress().getPort();
        properties.add("crypto.market.base-url", () -> url);
        properties.add("spring.ai.ollama.base-url", () -> url);
        properties.add("crypto.news.coindesk-url", () -> url + "/news-fixture/crypto");
        properties.add("crypto.news.fed-url", () -> url + "/news-fixture/fed");
        properties.add("crypto.news.cointelegraph-url", () -> url + "/news-fixture/cointelegraph");
        properties.add("crypto.news.ethereum-url", () -> url + "/news-fixture/ethereum");
        properties.add("crypto.news.sec-url", () -> url + "/news-fixture/sec");
    }

    @AfterAll
    static void closeUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void filtersMarketRelevantNewsAcrossAssetsAndAnalyzesOnlyTheSelectedArticle() {
        var feed = client.getForObject("/api/news", JsonNode.class);
        var items = feed.path("items");
        assertThat(items.findValuesAsText("title"))
                .containsExactly("Solana network update", "Bitcoin ETF inflows increase",
                        "Ethereum token unlock announced", "XRP listing begins on exchange",
                        "SEC proposes crypto custody regulation", "Ethereum mainnet upgrade announced",
                        "Federal Reserve statement");
        assertThat(items.findValuesAsText("topic"))
                .containsExactly("网络与安全", "市场与资金", "网络与安全", "市场与资金", "监管动态", "网络与安全", "宏观政策");
        assertThat(items.get(1).path("keywords").toString()).contains("ETF", "基金流向");
        assertThat(items.get(0).path("assets").get(0).asText()).isEqualTo("SOL");
        assertThat(items.findValuesAsText("source"))
                .contains("CoinDesk", "Cointelegraph", "SEC", "Ethereum Foundation", "美联储");
        assertThat(items.get(0).path("summary").asText())
                .isEqualTo("Solana validators published an update. It enters testing on Wednesday before any mainnet rollout.");
        assertThat(feed.path("unavailableSources")).isEmpty();
        assertThat(NEWS_PROMPT.get()).isNull();
        assertThat(feed.path("briefs").isEmpty()).isTrue();

        String id = items.get(0).path("id").asText();
        String legacyInput = "Solana network update\nSolana validators published an update.\n"
                + items.get(0).path("publishedAt").asText();
        jdbc.update("INSERT INTO news_brief (article_id, input_hash, headline, summary, commentary) VALUES (?, ?, ?, ?, ?)",
                UUID.fromString(id), UUID.nameUUIDFromBytes(legacyInput.getBytes(StandardCharsets.UTF_8)),
                "旧版中文标题", "旧版简报", "旧版解读");
        assertThat(client.getForObject("/api/news", JsonNode.class).path("previousBriefs").path(id).path("summary").asText())
                .isEqualTo("旧版简报");
        assertThat(client.getForObject("/api/news?language=en-US", JsonNode.class).path("previousBriefs").has(id)).isFalse();
        var response = client.postForEntity("/api/news/" + id + "/analysis", null, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("articleId").asText()).isEqualTo(id);
        var brief = response.getBody().path("brief");
        assertThat(brief.path("headline").asText()).isEqualTo("Solana 发布网络更新");
        assertThat(brief.path("summary").asText()).contains("验证者发布");
        assertThat(brief.path("commentary").asText()).contains("实际部署");
        assertThat(NEWS_PROMPT.get()).contains("Solana network update", "It enters testing on Wednesday")
                .doesNotContain("Bitcoin ETF inflows increase", "Ethereum token unlock announced", "Federal Reserve statement");
        assertThat(NEWS_FORMAT.get().path("required").toString()).isEqualTo("[\"headline\",\"summary\",\"commentary\"]");
        var refreshed = client.getForObject("/api/news", JsonNode.class);
        assertThat(refreshed.path("briefs").path(id)).isEqualTo(brief);
        assertThat(refreshed.path("previousBriefs").has(id)).isFalse();
        assertThat(client.postForObject("/api/news/" + id + "/analysis", null, JsonNode.class).path("brief"))
                .isEqualTo(brief);
        assertThat(NEWS_MODEL_CALLS.get()).isEqualTo(1);

        String english = "?language=en-US";
        assertThat(client.getForObject("/api/news" + english, JsonNode.class).path("briefs").has(id)).isFalse();
        var translated = client.postForObject("/api/news/" + id + "/analysis" + english, null, JsonNode.class);
        assertThat(translated.path("brief").path("headline").asText()).isEqualTo("Solana validators publish network update");
        assertThat(translated.path("brief").path("summary").asText()).contains("Wednesday", "mainnet");
        assertThat(translated.path("content").asText()).contains("### AI commentary");
        assertThat(client.getForObject("/api/news" + english, JsonNode.class).path("briefs").path(id))
                .isEqualTo(translated.path("brief"));
        assertThat(client.getForObject("/api/news", JsonNode.class).path("briefs").path(id)).isEqualTo(brief);
        assertThat(client.postForObject("/api/news/" + id + "/analysis" + english, null, JsonNode.class).path("brief"))
                .isEqualTo(translated.path("brief"));
        assertThat(NEWS_MODEL_CALLS.get()).isEqualTo(2);
        assertThat(client.getForEntity("/api/news?language=unsupported", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(client.postForEntity("/api/news/00000000-0000-0000-0000-000000000000/analysis", null, JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void servesScheduleSettingsWhenVectorStoreIsDisabled() {
        String endpoint = "/api/tasks/portfolio-reflection";
        var initial = client.getForEntity(endpoint, JsonNode.class);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody().path("available").asBoolean()).isFalse();
        assertThat(initial.getBody().path("nextRunAt").isNull()).isTrue();
        assertThat(client.postForEntity(endpoint + "/run", null, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        var invalid = new HttpEntity<>(Map.of("enabled", true, "cron", "invalid"));
        assertThat(client.exchange(endpoint, HttpMethod.PUT, invalid, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        var replacement = new HttpEntity<>(Map.of("enabled", false, "cron", "0 30 2 * * *"));
        assertThat(client.exchange(endpoint, HttpMethod.PUT, replacement, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var saved = client.getForObject(endpoint, JsonNode.class);
        assertThat(saved.path("enabled").asBoolean()).isFalse();
        assertThat(saved.path("cron").asText()).isEqualTo("0 30 2 * * *");
    }

    @Test
    void servesCryptoCandlesAndFillsFractionalOrdersUsingServerQuotes() {
        var candle = client.getForObject("/api/kline/BTCUSDT?period=4h&days=2", JsonNode.class);
        assertThat(candle.path("ohlc").size()).isEqualTo(2);
        assertThat(candle.path("ohlc").get(0).get(0).decimalValue()).isEqualByComparingTo("100");
        var intraday = client.getForObject("/api/kline/BTCUSDT?period=15m&days=2", JsonNode.class).path("dates");
        assertThat(intraday.size()).isEqualTo(2);
        assertThat(Duration.between(Instant.parse(intraday.get(0).asText()), Instant.parse(intraday.get(1).asText())))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(client.getForEntity("/api/kline/600519", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        var buy = client.postForEntity("/api/trading/buy",
                Map.of("accountId", 1, "stockCode", "ethusdt", "quantity", "0.125", "price", "1"), JsonNode.class);
        assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decimal(buy.getBody(), "price")).isEqualByComparingTo("100");
        assertThat(decimal(buy.getBody(), "fee")).isEqualByComparingTo("0.0125");
        assertThat(Instant.parse(buy.getBody().required("executedAt").asText()))
                .isBetween(Instant.now().minusSeconds(30), Instant.now());

        var oversell = client.postForEntity("/api/trading/sell",
                Map.of("accountId", 1, "stockCode", "ETHUSDT", "quantity", "0.126"), JsonNode.class);
        assertThat(oversell.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var sell = client.postForEntity("/api/trading/sell",
                Map.of("accountId", 1, "stockCode", "ETHUSDT", "quantity", "0.125"), JsonNode.class);
        assertThat(sell.getStatusCode()).isEqualTo(HttpStatus.OK);
        var account = client.getForObject("/api/trading/account/1", JsonNode.class);
        assertThat(account.path("positions").findValuesAsText("stockCode")).doesNotContain("ETHUSDT");
    }

    @Test
    void resumesFailedRunAndExecutesOneOrderPerReport() throws IOException {
        FAIL_FINAL_STEP.set(true);
        String failed = client.postForObject("/api/analysis/stream", Map.of("stockCode", "BTCUSDT"), String.class);
        assertThat(failed).contains("event:REPORT_CREATED", "event:NODE_ERROR", "event:GRAPH_ERROR")
                .doesNotContain("event:RESULT");
        var initialPlan = JSON.readTree(event(failed, "PIPELINE_PLAN").path("data").asText());
        assertThat(initialPlan.path("stages").findValuesAsText("id"))
                .containsExactly("stage_1", "stage_2", "stage_3", "stage_4", "stage_5");
        assertThat(event(failed, "PHASE_COMPLETE").path("stageId").asText()).isEqualTo("stage_1");
        assertThat(event(failed, "NODE_ERROR").path("stageId").asText()).isEqualTo("stage_5");
        assertThat(TECHNICAL_PROMPT.get())
                .contains("15m 最近", "15m · SMA20", "「支撑位」和「阻力位」",
                        "**15 分钟**、**1 小时**、**4 小时**、**1 日**", "可核验的依据")
                .doesNotContain("987654321", "请单列");
        assertThat(DEBATE_PROMPT.get()).contains("后续轮次只补充新证据、反驳或结论变化");
        assertThat(FINAL_PROMPT.get()).contains("不逐段复述", "15 分钟价位和其他周期放在同一组")
                .doesNotContain("单列 15 分钟");
        long reportId = event(failed, "REPORT_CREATED").path("data").asLong();

        var report = client.getForObject("/api/reports/" + reportId, JsonNode.class);
        assertThat(report.path("status").asText()).isEqualTo("FAILED");
        assertThat(report.path("stage").asText()).isEqualTo("stage_4");

        String resumed = client.postForObject("/api/analysis/resume/" + reportId + "/stream", null, String.class);
        var resumedPlan = JSON.readTree(event(resumed, "PIPELINE_PLAN").path("data").asText());
        assertThat(resumedPlan.path("completedStages").asInt()).isEqualTo(4);
        assertThat(resumedPlan.path("nodeReports").findValuesAsText("nodeName"))
                .contains("趋势与技术面", "多头研究员 (第1轮)", "空头研究员 (第2轮)", "模拟风控");
        JsonNode result = event(resumed, "RESULT");
        assertThat(result.path("reportId").asLong()).isEqualTo(reportId);
        assertThat(result.path("marketReport").asText())
                .contains("### 支撑位\n- **15 分钟**", "### 阻力位\n- **15 分钟**");
        assertThat(result.path("debate").asText()).contains("多头观点", "空头观点");
        assertThat(result.path("paperSignal").path("action").asText()).isEqualTo("BUY");
        assertThat(resumed).doesNotContain("\"nodeId\":\"market_analyst\"");

        String endpoint = "/api/trading/execute-decision/" + reportId;
        JsonNode first = client.postForObject(endpoint, null, JsonNode.class);
        JsonNode duplicate = client.postForObject(endpoint, null, JsonNode.class);
        assertThat(first.path("id").asLong()).isPositive().isEqualTo(duplicate.path("id").asLong());
        assertThat(decimal(first, "quantity")).isPositive();
    }

    private static JsonNode event(String response, String name) {
        for (String block : response.split("\\R\\R")) {
            if (!block.startsWith("event:" + name + "\n") && !block.startsWith("event:" + name + "\r\n")) continue;
            String data = block.lines().filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5)).findFirst().orElseThrow();
            try {
                return JSON.readTree(data);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("Missing event " + name + " in " + response);
    }

    private static BigDecimal decimal(JsonNode node, String key) {
        return new BigDecimal(node.required(key).asText());
    }

    private static HttpServer upstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v3/klines", exchange -> {
                long now = Instant.now().toEpochMilli();
                long interval = exchange.getRequestURI().getQuery().contains("interval=15m") ? 900_000L : 3_600_000L;
                List<List<Object>> candles = new ArrayList<>();
                for (int i = 60; i > 0; i--) {
                    long open = now - interval * i;
                    candles.add(List.of(open, "100", "102", "99", "101", "0.33", open + interval - 1_000L));
                }
                if (interval == 900_000L) {
                    candles.add(List.of(now, "101", "987654321", "99", "101", "0.12", now + interval));
                }
                respond(exchange, 200, candles);
            });
            server.createContext("/api/v3/ticker/24hr", exchange -> respond(exchange, 200, Map.of(
                    "lastPrice", "100", "priceChangePercent", "1.2", "highPrice", "102",
                    "lowPrice", "99", "quoteVolume", "123456", "closeTime", Instant.now().toEpochMilli())));
            server.createContext("/news-fixture/crypto", exchange -> respondRss(exchange, """
                    <item><title>Bitcoin ETF inflows increase</title><link>https://example.com/bitcoin</link>
                    <pubDate>Mon, 23 Nov 2026 10:00:00 GMT</pubDate><description>Bitcoin market news.</description></item>
                    <item><title>Solana network update</title><link>https://example.com/solana</link>
                    <pubDate>Mon, 23 Nov 2026 11:00:00 GMT</pubDate>
                    <description><![CDATA[<p>Solana validators published an update.</p>]]></description>
                    <content:encoded xmlns:content="http://purl.org/rss/1.0/modules/content/"><![CDATA[
                    <p>Solana validators published an update.</p><p>It enters testing on Wednesday before any mainnet rollout.</p>
                    ]]></content:encoded></item>
                    <item><title>Ethereum token unlock announced</title><link>https://example.com/ethereum</link>
                    <pubDate>Mon, 23 Nov 2026 09:30:00 GMT</pubDate></item>
                    <item><title>Sponsored: crypto token giveaway</title><link>https://example.com/sponsored</link>
                    <pubDate>Mon, 23 Nov 2026 11:30:00 GMT</pubDate><description>Promoted crypto ETF offer.</description></item>
                    <item><title>Bitcoin newsletter launches</title><link>https://example.com/newsletter</link>
                    <pubDate>Mon, 23 Nov 2026 12:30:00 GMT</pubDate></item>
                    <item><title>Biotech company completes acquisition</title><link>https://example.com/company</link>
                    <pubDate>Mon, 23 Nov 2026 13:30:00 GMT</pubDate></item>
                    """));
            server.createContext("/news-fixture/fed", exchange -> respondRss(exchange, """
                    <item><title>Federal Reserve statement</title><link>https://example.com/fed</link>
                    <pubDate>Mon, 23 Nov 2026 09:00:00 GMT</pubDate><description>Policy release.</description></item>
                    """));
            server.createContext("/news-fixture/cointelegraph", exchange -> respondRss(exchange, """
                    <item><title>XRP listing begins on exchange</title><link>https://example.com/listing</link>
                    <pubDate>Mon, 23 Nov 2026 09:25:00 GMT</pubDate></item>
                    """));
            server.createContext("/news-fixture/sec", exchange -> respondRss(exchange, """
                    <item><title>SEC proposes crypto custody regulation</title><link>https://example.com/regulation</link>
                    <pubDate>Mon, 23 Nov 2026 09:20:00 GMT</pubDate></item>
                    """));
            server.createContext("/news-fixture/ethereum", exchange -> respondRss(exchange, """
                    <item><title>Ethereum mainnet upgrade announced</title><link>https://example.com/mainnet</link>
                    <pubDate>Mon, 23 Nov 2026 09:15:00 GMT</pubDate></item>
                    """));
            server.createContext("/api/chat", exchange -> {
                JsonNode body = JSON.readTree(exchange.getRequestBody());
                JsonNode messages = body.path("messages");
                String system = messages.get(0).path("content").asText();
                String userPrompt = messages.get(1).path("content").asText();
                boolean technicalNode = system.startsWith("你负责现货技术分析");
                boolean finalNode = system.contains("SIZE:");
                boolean newsNode = system.contains("只分析用户提供的这一条新闻");
                if (newsNode) {
                    NEWS_PROMPT.set(userPrompt);
                    NEWS_FORMAT.set(body.path("format"));
                    NEWS_MODEL_CALLS.incrementAndGet();
                }
                if (technicalNode) TECHNICAL_PROMPT.set(userPrompt);
                if (system.startsWith("从现货角度说明支持上涨")) DEBATE_PROMPT.set(userPrompt);
                if (finalNode) FINAL_PROMPT.set(userPrompt);
                if (finalNode && FAIL_FINAL_STEP.getAndSet(false)) {
                    respond(exchange, 400, Map.of("error", "intentional model fixture error"));
                    return;
                }
                String content = "根据给定数据准备的测试分析。";
                if (newsNode) content = """
                        {"headline":"Solana 发布网络更新","summary":"Solana 验证者发布了一则更新。",
                         "commentary":"实际影响取决于更新的实际部署情况。"}
                        """;
                if (newsNode && system.contains("英语（English）")) content = """
                        {"headline":"Solana validators publish network update",
                         "summary":"Validators published a network update. Testing starts on Wednesday. The feed does not specify a mainnet rollout date.",
                         "commentary":"The effect depends on deployment. Watch for test results and a confirmed rollout schedule."}
                        """;
                if (finalNode) content = "ACTION: BUY\nSIZE: 10%\n\n模拟测试研报。";
                if (technicalNode) content = """
                        ### 支撑位
                        - **15 分钟** 99 USDT，已收盘的低点多次到达。
                        - **1 小时** 99 USDT，历史低点与短周期一致。

                        ### 阻力位
                        - **15 分钟** 102 USDT，接近近期高点。
                        - **1 小时** 102 USDT，有前高作为依据。
                        """;
                respond(exchange, 200, Map.of("model", "qwen2.5:7b", "created_at", Instant.now().toString(),
                        "message", Map.of("role", "assistant", "content", content), "done", true,
                        "done_reason", "stop", "prompt_eval_count", 10, "eval_count", 20));
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] bytes = JSON.writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private static void respondRss(HttpExchange exchange, String items) throws IOException {
        byte[] bytes = ("<rss version=\"2.0\"><channel>" + items + "</channel></rss>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/rss+xml; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
