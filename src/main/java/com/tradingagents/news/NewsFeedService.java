package com.tradingagents.news;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Element;

import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
public class NewsFeedService {

    private static final Logger log = LoggerFactory.getLogger(NewsFeedService.class);
    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(55);
    private static final int SOURCE_TEXT_LIMIT = 4_000;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final List<Source> sources;
    private final Map<Source, List<Article>> lastGood = new LinkedHashMap<>();
    private Feed cached = new Feed(List.of(), null, List.of());
    private Instant nextRefresh = Instant.EPOCH;

    public NewsFeedService(@Value("${crypto.news.coindesk-url}") URI coinDesk,
                           @Value("${crypto.news.fed-url}") URI federalReserve,
                           @Value("${crypto.news.cointelegraph-url}") URI cointelegraph,
                           @Value("${crypto.news.ethereum-url}") URI ethereumFoundation,
                           @Value("${crypto.news.sec-url}") URI sec) {
        sources = List.of(
                new Source("CoinDesk", coinDesk, false),
                new Source("Cointelegraph", cointelegraph, false),
                new Source("Ethereum Foundation", ethereumFoundation, false),
                new Source("SEC", sec, false),
                new Source("美联储", federalReserve, true));
    }

    public synchronized Feed latest() {
        Instant startedAt = Instant.now();
        if (startedAt.isBefore(nextRefresh)) return cached;
        List<String> unavailable = new ArrayList<>();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = sources.stream().map(source -> CompletableFuture.supplyAsync(() -> refresh(source), workers))
                    .toList();
            for (var request : requests) {
                var update = request.join();
                if (update.articles() == null) unavailable.add(update.source().name());
                else lastGood.put(update.source(), update.articles());
            }
        }

        Map<UUID, Article> articles = new LinkedHashMap<>();
        lastGood.values().stream().flatMap(List::stream)
                .sorted(Comparator.comparing(Article::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(article -> articles.putIfAbsent(article.id(), article));
        Instant checkedAt = Instant.now();
        Instant updatedAt = unavailable.size() < sources.size() ? checkedAt : cached.updatedAt();
        cached = new Feed(List.copyOf(articles.values()), updatedAt, List.copyOf(unavailable));
        nextRefresh = startedAt.plus(REFRESH_INTERVAL);
        return cached;
    }

    private SourceUpdate refresh(Source source) {
        try {
            return new SourceUpdate(source, fetch(source));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            log.warn("News feed unavailable: {} ({})", source.name(), error.getClass().getSimpleName());
        }
        return new SourceUpdate(source, null);
    }

    public synchronized Article article(UUID id) {
        return cached.items().stream().filter(item -> item.id().equals(id)).findFirst()
                .or(() -> latest().items().stream().filter(item -> item.id().equals(id)).findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该新闻已离开快讯列表，请刷新后重试"));
    }

    private List<Article> fetch(Source source) throws Exception {
        var request = HttpRequest.newBuilder(source.url()).timeout(Duration.ofSeconds(7))
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .header("User-Agent", "CryptoTradingAgents/1.0 (RSS reader)").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body().length > 2_000_000) {
            throw new IOException("Unexpected RSS response: " + response.statusCode());
        }
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.body()));
        if (!"rss".equals(document.getDocumentElement().getTagName())) throw new IOException("Expected RSS feed");

        List<Article> articles = new ArrayList<>();
        var entries = document.getElementsByTagName("item");
        for (int i = 0; i < entries.getLength(); i++) {
            var entry = (Element) entries.item(i);
            String title = text(entry, "title");
            String rawLink = text(entry, "link");
            if (title.isBlank() || rawLink.isBlank()) continue;
            URI link;
            try {
                link = response.uri().resolve(rawLink);
            } catch (IllegalArgumentException malformedLink) {
                continue;
            }
            if (link.getHost() == null || !List.of("https", "http").contains(link.getScheme())) continue;
            String summary = plainText(text(entry, "description"));
            String originalSummary = clip(summary, 1_200);
            String articleText = plainText(text(entry, "content:encoded"));
            if (articleText.length() > summary.length()) summary = articleText;
            String topic = NewsRelevance.classify(title, summary, source.monetaryPolicy());
            if (topic == null) continue;
            UUID id = UUID.nameUUIDFromBytes(link.toString().getBytes(StandardCharsets.UTF_8));
            articles.add(new Article(id, source.name(), title, link.toString(),
                    date(text(entry, "pubDate")), summary, topic,
                    NewsRelevance.keywords(title, summary, source.monetaryPolicy()),
                    NewsRelevance.assets(title, summary), originalSummary));
        }
        return List.copyOf(articles);
    }

    private static String text(Element entry, String tag) {
        var field = entry.getElementsByTagName(tag).item(0);
        return field == null ? "" : field.getTextContent().strip();
    }

    private static Instant date(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private static String plainText(String html) throws IOException {
        if (html.isBlank()) return "";
        var result = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleText(char[] data, int position) {
                if (!result.isEmpty()) result.append(' ');
                result.append(data);
            }
        }, true);
        String normalized = result.toString().replaceAll("\\s+", " ").strip();
        return clip(normalized, SOURCE_TEXT_LIMIT);
    }

    private static String clip(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit).stripTrailing() + "…";
    }

    private record Source(String name, URI url, boolean monetaryPolicy) {}
    private record SourceUpdate(Source source, List<Article> articles) {}

    public record Article(UUID id, String source, String title, String url, Instant publishedAt, String summary,
                          String topic, List<String> keywords, List<String> assets, @JsonIgnore String originalSummary) {}

    public record Feed(List<Article> items, Instant updatedAt, List<String> unavailableSources) {}
}
