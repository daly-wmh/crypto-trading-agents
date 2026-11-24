package com.tradingagents.api.controller;

import com.tradingagents.news.NewsBriefService;
import com.tradingagents.news.NewsBriefService.Brief;
import com.tradingagents.news.NewsBriefService.BriefLanguage;
import com.tradingagents.news.NewsFeedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsFeedService news;
    private final NewsBriefService briefs;

    public NewsController(NewsFeedService news, NewsBriefService briefs) {
        this.news = news;
        this.briefs = briefs;
    }

    @GetMapping
    public Feed latest(@RequestParam(defaultValue = "zh-CN") String language) {
        var selected = BriefLanguage.fromCode(language);
        var feed = news.latest();
        var available = briefs.available(feed.items(), selected);
        return new Feed(feed.items(), feed.updatedAt(), feed.unavailableSources(), available.current(), available.previous());
    }

    @PostMapping("/{id}/analysis")
    public Analysis analyze(@PathVariable UUID id, @RequestParam(defaultValue = "zh-CN") String language) {
        var selected = BriefLanguage.fromCode(language);
        var brief = briefs.generate(news.article(id), selected);
        return new Analysis(id, brief.summary() + "\n\n### " + selected.commentaryTitle() + "\n\n" + brief.commentary(), brief);
    }

    public record Analysis(UUID articleId, String content, Brief brief) {}

    public record Feed(List<NewsFeedService.Article> items, Instant updatedAt, List<String> unavailableSources,
                       Map<UUID, Brief> briefs, Map<UUID, Brief> previousBriefs) {}
}
