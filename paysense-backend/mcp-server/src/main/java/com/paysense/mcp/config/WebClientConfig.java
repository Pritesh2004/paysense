package com.paysense.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${app.transaction-service.url}")
    private String transactionServiceUrl;

    @Value("${app.gemini.api-url}")
    private String geminiApiUrl;

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;

    /**
     * WebClient for calling Transaction Service with JWT forwarding.
     */
    @Bean(name = "transactionServiceClient")
    public WebClient transactionServiceClient() {
        return WebClient.builder()
                .baseUrl(transactionServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * WebClient for calling Claude/Anthropic API.
     */
    @Bean(name = "claudeApiClient") // keeping name to avoid breaking other injections
    public WebClient geminiApiClient() {
        // Increase buffer size for large AI responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(geminiApiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(strategies)
                .build();
    }
}
