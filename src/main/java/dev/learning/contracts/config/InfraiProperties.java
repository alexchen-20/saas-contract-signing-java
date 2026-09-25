package dev.learning.contracts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrai")
public record InfraiProperties(String baseUrl, String apiKey, Signing signing) {
    public record Signing(String reason, String location, int retentionDays) {}
}
