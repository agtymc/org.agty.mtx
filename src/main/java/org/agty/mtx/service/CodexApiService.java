package org.agty.mtx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.agty.mtx.config.CodexProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CodexApiService {
    private static final Logger log = LoggerFactory.getLogger(CodexApiService.class);

    private final CodexProperties codexProperties;
    private final ObjectMapper objectMapper;

    public CodexApiService(CodexProperties codexProperties) {
        this.codexProperties = codexProperties;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isCodexApiModel(String model) {
        ApiSettings settings = resolveSettings();
        if (!settings.enabled()) {
            return false;
        }
        String normalizedModel = normalize(model);
        String codexModel = normalize(settings.modelName());
        return !codexModel.isBlank() && codexModel.equals(normalizedModel);
    }

    public OllamaService.ChatResult chat(List<Map<String, String>> history, Double temperature) {
        ApiSettings settings = resolveSettings();
        String apiKey = settings.apiKey();
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Не задан OpenAI API key для codex.api");
        }
        log.info("Codex API chat start: model={}, baseUrl={}, messages={}",
                settings.openaiModel(),
                settings.baseUrl(),
                history == null ? 0 : history.size());

        Map<String, Object> request = buildRequest(history, temperature, false, settings);
        String response;
        try {
            response = buildClient(settings)
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(Math.max(10, settings.timeoutSeconds())))
                    .block();
        } catch (Exception ex) {
            log.warn("Codex API chat failed: {}", ex.getMessage());
            throw ex;
        }

        if (response == null || response.isBlank()) {
            return new OllamaService.ChatResult("", 0, 0, 0, 0, 0, 0, "");
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("error")) {
                JsonNode error = root.path("error");
                String message = error.path("message").asText("OpenAI API error");
                throw new IllegalStateException(message);
            }
            JsonNode first = root.path("choices").path(0);
            String content = first.path("message").path("content").asText("");
            String doneReason = first.path("finish_reason").asText("");
            long promptTokens = root.path("usage").path("prompt_tokens").asLong(0);
            long completionTokens = root.path("usage").path("completion_tokens").asLong(0);
            return new OllamaService.ChatResult(content, promptTokens, completionTokens, 0, 0, 0, 0, doneReason);
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка разбора ответа OpenAI: " + ex.getMessage(), ex);
        }
    }

    public Flux<OllamaService.StreamChunk> streamChat(List<Map<String, String>> history, Double temperature) {
        ApiSettings settings = resolveSettings();
        String apiKey = settings.apiKey();
        if (apiKey.isBlank()) {
            return Flux.error(new IllegalStateException("Не задан OpenAI API key для codex.api"));
        }
        log.info("Codex API stream start: model={}, baseUrl={}, messages={}, proxyEnabled={}",
                settings.openaiModel(),
                settings.baseUrl(),
                history == null ? 0 : history.size(),
                settings.proxyEnabled());

        Map<String, Object> request = buildRequest(history, temperature, true, settings);
        return buildClient(settings)
                .post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(ServerSentEvent::data)
                .flatMap(this::toStreamChunk)
                .timeout(Duration.ofSeconds(Math.max(10, settings.timeoutSeconds())))
                .doOnError(ex -> log.warn("Codex API stream failed: {}", ex.getMessage()));
    }

    private Mono<OllamaService.StreamChunk> toStreamChunk(String data) {
        if (data == null || data.isBlank() || "[DONE]".equals(data.trim())) {
            return Mono.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(data);
            if (node.has("error")) {
                String message = node.path("error").path("message").asText("OpenAI API stream error");
                return Mono.error(new IllegalStateException(message));
            }
            JsonNode choice = node.path("choices").path(0);
            if (choice.isMissingNode()) {
                return Mono.empty();
            }
            String content = choice.path("delta").path("content").asText("");
            String doneReason = choice.path("finish_reason").asText("");
            boolean done = !doneReason.isBlank();
            long promptTokens = node.path("usage").path("prompt_tokens").asLong(0);
            long completionTokens = node.path("usage").path("completion_tokens").asLong(0);
            return Mono.just(new OllamaService.StreamChunk(content, promptTokens, completionTokens, done, 0, 0, 0, 0, doneReason));
        } catch (Exception ex) {
            return Mono.error(new IllegalStateException("Ошибка парсинга stream chunk: " + ex.getMessage(), ex));
        }
    }

    private Map<String, Object> buildRequest(List<Map<String, String>> history, Double temperature, boolean stream, ApiSettings settings) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", settings.openaiModel());
        request.put("messages", sanitizeMessages(history));
        request.put("stream", stream);
        if (temperature != null) {
            request.put("temperature", temperature);
        }
        return request;
    }

    private List<Map<String, String>> sanitizeMessages(List<Map<String, String>> history) {
        List<Map<String, String>> sanitized = new ArrayList<>();
        if (history == null) {
            return sanitized;
        }
        for (Map<String, String> item : history) {
            if (item == null) {
                continue;
            }
            String role = normalize(item.get("role"));
            String content = item.get("content") == null ? "" : item.get("content").trim();
            if (content.isBlank()) {
                continue;
            }
            if (!"user".equals(role) && !"assistant".equals(role) && !"system".equals(role)) {
                continue;
            }
            Map<String, String> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            sanitized.add(msg);
        }
        return sanitized;
    }

    private WebClient buildClient(ApiSettings settings) {
        HttpClient httpClient = HttpClient.create();
        if (settings.proxyEnabled() && !isBlank(settings.proxyHost()) && settings.proxyPort() > 0) {
            httpClient = httpClient.proxy(spec -> {
                ProxyProvider.Builder builder = spec.type(ProxyProvider.Proxy.HTTP)
                        .host(settings.proxyHost().trim())
                        .port(settings.proxyPort());
                if (!isBlank(settings.proxyUsername())) {
                    builder.username(settings.proxyUsername().trim());
                    if (!isBlank(settings.proxyPassword())) {
                        builder.password(unused -> settings.proxyPassword());
                    }
                }
            });
        }

        return WebClient.builder()
                .baseUrl(settings.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + settings.apiKey())
                .build();
    }

    private ApiSettings resolveSettings() {
        ApiSettings base = new ApiSettings(
                codexProperties.getApi().isEnabled(),
                safe(codexProperties.getApi().getModelName(), "codex:api"),
                safe(codexProperties.getApi().getBaseUrl(), "https://api.openai.com/v1"),
                safe(codexProperties.getApi().getApiKey(), ""),
                safe(codexProperties.getApi().getOpenaiModel(), "gpt-5.5"),
                Math.max(10, codexProperties.getApi().getTimeoutSeconds()),
                codexProperties.getApi().getProxy() != null && codexProperties.getApi().getProxy().isEnabled(),
                codexProperties.getApi().getProxy() == null ? "" : safe(codexProperties.getApi().getProxy().getHost(), ""),
                codexProperties.getApi().getProxy() == null ? 8080 : codexProperties.getApi().getProxy().getPort(),
                codexProperties.getApi().getProxy() == null ? "" : safe(codexProperties.getApi().getProxy().getUsername(), ""),
                codexProperties.getApi().getProxy() == null ? "" : safe(codexProperties.getApi().getProxy().getPassword(), "")
        );

        Map<String, String> ini = readIniConfig();
        String apiKey = pick(ini.get("codex.api.api_key"), base.apiKey());
        if (isBlank(apiKey)) {
            apiKey = safe(System.getenv("OPENAI_API_KEY"), "");
        }
        return new ApiSettings(
                parseBooleanOrDefault(ini.get("codex.api.enable"), base.enabled()),
                pick(ini.get("codex.api.model_name"), base.modelName()),
                pick(ini.get("codex.api.base_url"), base.baseUrl()),
                apiKey,
                pick(ini.get("codex.api.openai_model"), pick(ini.get("codex.api.model"), base.openaiModel())),
                parseIntOrDefault(ini.get("codex.api.timeout_seconds"), base.timeoutSeconds()),
                parseBooleanOrDefault(ini.get("codex.api.proxy_enabled"), base.proxyEnabled()),
                pick(ini.get("codex.api.proxy_host"), base.proxyHost()),
                parseIntOrDefault(ini.get("codex.api.proxy_port"), base.proxyPort()),
                pick(ini.get("codex.api.proxy_username"), base.proxyUsername()),
                pick(ini.get("codex.api.proxy_password"), base.proxyPassword())
        );
    }

    private Map<String, String> readIniConfig() {
        Map<String, String> values = new HashMap<>();
        String configPathRaw = codexProperties.getApi().getConfigPath();
        Path configPath = Path.of(safe(configPathRaw, "./config.ini"));
        if (!Files.exists(configPath)) {
            return values;
        }
        try {
            List<String> lines = Files.readAllLines(configPath);
            for (String line : lines) {
                String normalized = line == null ? "" : line.trim();
                if (normalized.isEmpty() || normalized.startsWith("#") || normalized.startsWith(";") || normalized.startsWith("[")) {
                    continue;
                }
                int eq = normalized.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = normalized.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = normalized.substring(eq + 1).trim();
                values.put(key, value);
            }
        } catch (IOException ex) {
            log.warn("Cannot read codex api ini config {}: {}", configPath, ex.getMessage());
        }
        return values;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private String pick(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred.trim();
    }

    private int parseIntOrDefault(String value, int fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean parseBooleanOrDefault(String value, boolean fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private record ApiSettings(
            boolean enabled,
            String modelName,
            String baseUrl,
            String apiKey,
            String openaiModel,
            int timeoutSeconds,
            boolean proxyEnabled,
            String proxyHost,
            int proxyPort,
            String proxyUsername,
            String proxyPassword
    ) {
    }
}
