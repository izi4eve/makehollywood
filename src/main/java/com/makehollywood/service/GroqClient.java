package com.makehollywood.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GroqClient {

    private final WebClient webClient;
    private final String model;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public GroqClient(
            @Value("${groq.api.url}") String apiUrl,
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.model}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public String chat(String systemPrompt, String userMessage) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.3,
                "max_tokens", 1000,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            Map response = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class).map(errorBody -> {
                                int status = clientResponse.statusCode().value();
                                if (status == 429) {
                                    return new GroqRateLimitException("Groq rate limit reached");
                                }
                                return new GroqClientException("Groq client error " + status + ": " + errorBody);
                            })
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class).map(errorBody ->
                                    new GroqServerException("Groq server error: " + errorBody)
                            )
                    )
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            List<Map> choices = (List<Map>) response.get("choices");
            Map message = (Map) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (GroqRateLimitException | GroqClientException | GroqServerException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                throw new GroqTimeoutException("Groq request timed out after 30 seconds");
            }
            log.error("Unexpected Groq error: {}", e.getMessage());
            throw new GroqServerException("Unexpected error communicating with AI: " + e.getMessage());
        }
    }

    // Исключения
    public static class GroqRateLimitException extends RuntimeException {
        public GroqRateLimitException(String message) { super(message); }
    }

    public static class GroqTimeoutException extends RuntimeException {
        public GroqTimeoutException(String message) { super(message); }
    }

    public static class GroqClientException extends RuntimeException {
        public GroqClientException(String message) { super(message); }
    }

    public static class GroqServerException extends RuntimeException {
        public GroqServerException(String message) { super(message); }
    }
}