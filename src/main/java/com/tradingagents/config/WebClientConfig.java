package com.tradingagents.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    static {
        // 禁用JVM系统代理自动检测，防止Java读取Windows注册表中的代理设置(127.0.0.1:7897)
        // useSystemProxies=false 是关键：阻止JVM从OS自动获取代理
        System.setProperty("java.net.useSystemProxies", "false");
        // 同时清除显式的代理属性
        String[] proxyProps = {"http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
                "http.nonProxyHosts", "https.nonProxyHosts"};
        for (String prop : proxyProps) {
            String old = System.getProperty(prop);
            System.clearProperty(prop);
            if (old != null && !old.isEmpty()) {
                log.info("Cleared JVM proxy property: {} = '{}'", prop, old);
            }
        }
        log.info("JVM proxy disabled: useSystemProxies=false");
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(15));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader(HttpHeaders.REFERER, "https://quote.eastmoney.com/")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
    }
}
