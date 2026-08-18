package com.pride.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ai-server.base-url}")
    private String aiServerBaseUrl;

    @Bean
    public WebClient aiServerWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // base64 이미지 응답 대응 (기본 256KB → 10MB)
                .build();

        return WebClient.builder()
                .baseUrl(aiServerBaseUrl)
                .exchangeStrategies(strategies)
                .build();
    }
}