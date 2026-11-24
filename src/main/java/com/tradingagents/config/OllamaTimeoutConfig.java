package com.tradingagents.config;

import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Ollama HTTP客户端超时配置
 * 本地模型并行请求时需要较长的读取超时(排队等待)
 */
@Configuration
public class OllamaTimeoutConfig {

    @Bean
    public RestClientCustomizer ollamaRestClientCustomizer() {
        return restClientBuilder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(300));
            restClientBuilder.requestFactory(factory);
        };
    }
}
