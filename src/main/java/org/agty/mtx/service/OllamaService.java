package org.agty.mtx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OllamaService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OllamaService(@Value("${ollama.api.url}") String ollamaUrl) {
        this.webClient = WebClient.builder().baseUrl(ollamaUrl).build();
        this.objectMapper = new ObjectMapper();
    }

    public ChatResult chat(String model, List<Map<String, String>> messages, Double temperature) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", messages);
            request.put("stream", false);

            Map<String, Object> options = new HashMap<>();
            if (temperature != null) {
                options.put("temperature", temperature);
            }
            request.put("options", options);

            String response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                return new ChatResult("", 0, 0, 0, 0, 0, 0, "");
            }

            JsonNode root = objectMapper.readTree(response);
            long promptTokens = root.path("prompt_eval_count").asLong(0);
            long completionTokens = root.path("eval_count").asLong(0);
            long totalDurationNs = root.path("total_duration").asLong(0);
            long loadDurationNs = root.path("load_duration").asLong(0);
            long promptEvalDurationNs = root.path("prompt_eval_duration").asLong(0);
            long evalDurationNs = root.path("eval_duration").asLong(0);
            String doneReason = root.path("done_reason").asText("");
            JsonNode messageNode = root.path("message");
            JsonNode contentNode = messageNode.path("content");
            if (!contentNode.isMissingNode()) {
                return new ChatResult(
                        contentNode.asText(""),
                        promptTokens,
                        completionTokens,
                        totalDurationNs,
                        loadDurationNs,
                        promptEvalDurationNs,
                        evalDurationNs,
                        doneReason
                );
            }

            JsonNode legacyResponseNode = root.path("response");
            return new ChatResult(
                    legacyResponseNode.asText(""),
                    promptTokens,
                    completionTokens,
                    totalDurationNs,
                    loadDurationNs,
                    promptEvalDurationNs,
                    evalDurationNs,
                    doneReason
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка запроса к Ollama: " + ex.getMessage(), ex);
        }
    }

    public Flux<StreamChunk> streamChat(String model, List<Map<String, String>> messages, Double temperature) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("stream", true);

        Map<String, Object> options = new HashMap<>();
        if (temperature != null) {
            options.put("temperature", temperature);
        }
        request.put("options", options);

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .map(node -> new StreamChunk(
                        node.path("message").path("content").asText(""),
                        node.path("prompt_eval_count").asLong(0),
                        node.path("eval_count").asLong(0),
                        node.path("done").asBoolean(false),
                        node.path("total_duration").asLong(0),
                        node.path("load_duration").asLong(0),
                        node.path("prompt_eval_duration").asLong(0),
                        node.path("eval_duration").asLong(0),
                        node.path("done_reason").asText("")
                ));
    }

    public List<String> listLocalModels() {
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode modelsNode = root.path("models");
            if (!modelsNode.isArray()) {
                return List.of();
            }

            Set<String> names = new LinkedHashSet<>();
            for (JsonNode modelNode : modelsNode) {
                String name = modelNode.path("name").asText("");
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
            return names.stream().collect(Collectors.toList());
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка получения списка моделей: " + ex.getMessage(), ex);
        }
    }

    public record ChatResult(
            String content,
            long promptTokens,
            long completionTokens,
            long totalDurationNs,
            long loadDurationNs,
            long promptEvalDurationNs,
            long evalDurationNs,
            String doneReason
    ) {
    }

    public record StreamChunk(
            String content,
            long promptTokens,
            long completionTokens,
            boolean done,
            long totalDurationNs,
            long loadDurationNs,
            long promptEvalDurationNs,
            long evalDurationNs,
            String doneReason
    ) {
    }
}
