package dev.learning.contracts.infrai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.contracts.config.InfraiProperties;
import dev.learning.contracts.domain.ContractSigner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class InfraiPdfSigner implements ContractSigner {
    private static final int MAX_ATTEMPTS = 4;
    private final HttpClient http;
    private final ObjectMapper json;
    private final InfraiProperties properties;

    public InfraiPdfSigner(InfraiProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiPdfSigner(InfraiProperties properties, ObjectMapper json, HttpClient http) {
        this.properties = properties;
        this.json = json;
        this.http = http;
    }

    @Override
    public JsonNode sign(byte[] pdf, String certPem, String keyPem, String idempotencyKey) {
        var payload = json.createObjectNode()
                .put("pdf", Base64.getEncoder().encodeToString(pdf))
                .put("text", "Approved tenant agreement")
                .put("opacity", 0.18)
                .put("position", "center");

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/v1/pdf/watermark"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode signing request", exception);
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpResponse<String> response = send(request);
            JsonNode envelope = decodeEnvelope(response.body());

            if (response.statusCode() == 429) {
                pause(response, attempt);
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                JsonNode error = envelope.path("error");
                String code = error.path("code").asText("request rejected");
                throw new InfraiException(code, response.statusCode(), error);
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Signing service returned HTTP " + response.statusCode());
            }
            return envelope.path("data");
        }
        throw new IllegalStateException("Signing retry budget exhausted");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Signing transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Signing request interrupted", exception);
        }
    }

    private JsonNode decodeEnvelope(String body) {
        try {
            return json.readTree(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Signing response was not a JSON envelope", exception);
        }
    }

    private void pause(HttpResponse<?> response, int attempt) {
        long retryAfterSeconds = response.headers().firstValue("Retry-After")
                .map(this::parseRetryAfter)
                .orElse(1L << attempt);
        try {
            Thread.sleep(Math.min(retryAfterSeconds, 30) * 1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Signing retry interrupted", exception);
        }
    }

    private long parseRetryAfter(String value) {
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
