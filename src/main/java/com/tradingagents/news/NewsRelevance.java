package com.tradingagents.news;

import java.util.regex.Pattern;
import java.util.List;
import java.util.stream.Stream;

final class NewsRelevance {

    private static final Pattern PROMOTION = terms(
            "sponsored|partner content|advertorial|giveaway|promo code|token presale|how to buy|what is|beginner'?s guide");
    private static final Pattern MACRO = terms(
            "FOMC|Federal Reserve|rate cuts?|rate hikes?|interest[- ]rate decisions?|central bank rate|"
                    + "inflation (?:data|report|surprise)|CPI|consumer price index|nonfarm payrolls?|"
                    + "(?:U\\.S\\. |US )?Treasury yields?|dollar index");
    private static final Pattern CRYPTO = terms(
            "crypto(?:currency|currencies)?|bitcoin|BTC|Ethereum|ETH|Solana|SOL|XRP|Dogecoin|DOGE|BNB|"
                    + "blockchain|digital assets?|tokens?|tokeniz\\w*|stablecoins?|DeFi|on[- ]chain|Binance|Coinbase|"
                    + "Kraken|OKX|Hyperliquid|Aave|Cardano|Avalanche|Chainlink|Litecoin|Monero|Zcash");
    private static final Pattern POLICY = terms(
            "SEC|CFTC|MiCA|regulat\\w*|licen[cs]\\w*|lawsuit|court ruling|legislation|bill|sanctions?|"
                    + "ban(?:s|ned|ning)?|approv\\w*|enforcement");
    private static final Pattern MARKET = terms(
            "ETFs?|ETPs?|inflows?|outflows?|liquidat\\w*|open interest|funding rates?|liquidity|"
                    + "buybacks?|treasury|acquisition|(?:bought|sells?|purchases?)|capital raise|"
                    + "delist\\w*|list(?:s|ed|ing)|trading (?:halt|suspension)|withdrawal freeze|"
                    + "bankruptcy|insolven\\w*|rebalances?|record (?:high|low)");
    private static final Pattern PROTOCOL = terms(
            "exploits?|hacks?|breach|security incident|outage|depeg\\w*|hard forks?|network (?:upgrade|update)|"
                    + "protocol upgrade|mainnet|testnet|token unlocks?|token burns?|governance vote|"
                    + "supply (?:change|increase|reduction)|emergency patch");
    private static final List<Tag> KEYWORDS = List.of(
            tag("利率", "rates?|FOMC|monetary policy"),
            tag("通胀", "inflation|CPI|consumer price index"),
            tag("就业", "payrolls?|unemployment|jobs report"),
            tag("美元与收益率", "Treasury yields?|dollar index"),
            tag("ETF", "ETFs?|ETPs?"),
            tag("基金流向", "inflows?|outflows?"),
            tag("流动性", "liquidity|liquidat\\w*|funding rates?|open interest"),
            tag("稳定币", "stablecoins?|USDT|USDC|depeg\\w*"),
            tag("上市与下架", "list(?:s|ed|ing)|delist\\w*"),
            tag("交易所公告", "trading (?:halt|suspension)|withdrawal freeze|maintenance"),
            tag("资金动向", "treasury|acquisition|buybacks?|capital raise|purchases?"),
            tag("网络升级", "upgrades?|mainnet|testnet|hard forks?"),
            tag("安全事件", "exploits?|hacks?|breach|outage|emergency patch|security incident"),
            tag("代币解锁", "token unlocks?"),
            tag("执法", "enforcement|lawsuits?|charges?|court ruling|sanctions?"),
            tag("审批", "approv\\w*|licen[cs]\\w*"),
            tag("立法", "legislation|bill|regulat\\w*|MiCA"));
    private static final List<Tag> ASSETS = List.of(
            tag("BTC", "Bitcoin|BTC"), tag("ETH", "Ethereum|ETH"), tag("SOL", "Solana|SOL"),
            tag("BNB", "BNB"), tag("XRP", "XRP"), tag("DOGE", "Dogecoin|DOGE"),
            tag("ADA", "Cardano|ADA"), tag("AVAX", "Avalanche|AVAX"), tag("LINK", "Chainlink"),
            tag("HYPE", "Hyperliquid|HYPE token"), tag("LTC", "Litecoin|LTC"), tag("XMR", "Monero|XMR"),
            tag("ZEC", "Zcash|ZEC"), tag("TRX", "TRX|Tron blockchain"), tag("USDC", "USDC"),
            tag("USDT", "Tether|USDT"));

    private NewsRelevance() {}

    static String classify(String title, String summary, boolean monetaryPolicyFeed) {
        if (PROMOTION.matcher(title).find()) return null;
        String text = title + " " + summary;
        if (monetaryPolicyFeed || MACRO.matcher(text).find()) return "宏观政策";
        if (!CRYPTO.matcher(text).find()) return null;
        if (PROTOCOL.matcher(text).find()) return "网络与安全";
        if (POLICY.matcher(text).find()) return "监管动态";
        if (MARKET.matcher(text).find()) return "市场与资金";
        return null;
    }

    private static Pattern terms(String expression) {
        return Pattern.compile("\\b(?:" + expression + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    static List<String> keywords(String title, String summary, boolean monetaryPolicyFeed) {
        var present = matching(KEYWORDS, title + " " + summary);
        return (monetaryPolicyFeed ? Stream.concat(Stream.of("货币政策"), present) : present).toList();
    }

    static List<String> assets(String title, String summary) {
        return matching(ASSETS, title + " " + summary).toList();
    }

    private static Stream<String> matching(List<Tag> tags, String text) {
        return tags.stream().filter(tag -> tag.pattern().matcher(text).find()).map(Tag::label);
    }

    private static Tag tag(String label, String expression) {
        return new Tag(label, terms(expression));
    }

    private record Tag(String label, Pattern pattern) {}
}
