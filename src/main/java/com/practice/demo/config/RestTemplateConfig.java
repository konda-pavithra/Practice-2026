package com.practice.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Value("${yahoo.finance.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${yahoo.finance.read-timeout-ms:30000}")
    private int readTimeoutMs;

    /**
     * Dedicated RestTemplate for Yahoo Finance API calls.
     * Separate from any default bean so timeouts don't affect other callers.
     */
    @Bean("stockRestTemplate")
    public RestTemplate stockRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
