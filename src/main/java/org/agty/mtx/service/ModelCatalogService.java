package org.agty.mtx.service;

import org.agty.mtx.config.AppProperties;
import org.agty.mtx.config.CodexProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ModelCatalogService {

    private final AppProperties appProperties;
    private final OllamaService ollamaService;
    private final CodexProperties codexProperties;
    private List<String> models = new ArrayList<>();

    public ModelCatalogService(AppProperties appProperties, OllamaService ollamaService, CodexProperties codexProperties) {
        this.appProperties = appProperties;
        this.ollamaService = ollamaService;
        this.codexProperties = codexProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadModelsOnStartup() {
        refreshModels();
    }

    public synchronized void refreshModels() {
        try {
            List<String> loaded = ollamaService.listLocalModels();
            if (loaded != null && !loaded.isEmpty()) {
                models = appendCodexModel(loaded);
                if (!models.contains(appProperties.getDefaultModel())) {
                    appProperties.setDefaultModel(models.get(0));
                }
                return;
            }
        } catch (Exception ignored) {
        }

        List<String> fallback = appProperties.getModels() == null
                ? List.of()
                : appProperties.getModels();
        models = appendCodexModel(fallback);
    }

    public synchronized List<String> getModels() {
        return Collections.unmodifiableList(models);
    }

    public synchronized String getDefaultModel() {
        if (models.isEmpty()) {
            return appProperties.getDefaultModel();
        }
        if (models.contains(appProperties.getDefaultModel())) {
            return appProperties.getDefaultModel();
        }
        return models.get(0);
    }

    public synchronized String resolveModel(String requestedModel) {
        String candidate = requestedModel;
        if (candidate == null || candidate.isBlank()) {
            candidate = getDefaultModel();
        }

        if (models.contains(candidate)) {
            return candidate;
        }
        throw new IllegalArgumentException("Неизвестная модель: " + candidate);
    }

    private ArrayList<String> appendCodexModel(List<String> baseModels) {
        Set<String> merged = new LinkedHashSet<>();
        if (baseModels != null) {
            merged.addAll(baseModels);
        }
        Map<String, String> localIni = readIni(safe(codexProperties.getLocal().getConfigPath(), "./config.ini"));
        boolean localEnabled = parseBooleanOrDefault(localIni.get("codex.local.enable"), codexProperties.getLocal().isEnabled());
        String codexLocalModel = pick(localIni.get("codex.local.model_name"), safe(codexProperties.getLocal().getModelName(), "codex:local"));
        if (localEnabled && !codexLocalModel.isBlank()) {
            merged.add(codexLocalModel);
        }
        Map<String, String> apiIni = readIni(safe(codexProperties.getApi().getConfigPath(), "./config.ini"));
        boolean apiEnabled = parseBooleanOrDefault(apiIni.get("codex.api.enable"), codexProperties.getApi().isEnabled());
        String codexApiModel = pick(apiIni.get("codex.api.model_name"), safe(codexProperties.getApi().getModelName(), "codex:api"));
        if (apiEnabled && !codexApiModel.isBlank()) {
            merged.add(codexApiModel);
        }
        return new ArrayList<>(merged);
    }

    private Map<String, String> readIni(String pathRaw) {
        Map<String, String> values = new HashMap<>();
        Path path = Path.of(pathRaw);
        if (!Files.exists(path)) {
            return values;
        }
        try {
            List<String> lines = Files.readAllLines(path);
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
        } catch (IOException ignored) {
        }
        return values;
    }

    private boolean parseBooleanOrDefault(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
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

    private String safe(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private String pick(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty() ? fallback : preferred.trim();
    }
}
