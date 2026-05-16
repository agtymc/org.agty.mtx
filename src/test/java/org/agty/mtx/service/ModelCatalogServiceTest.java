package org.agty.mtx.service;

import org.agty.mtx.config.AppProperties;
import org.agty.mtx.config.CodexProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelCatalogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void refreshModels_appendsCodexAliasesFromIni() throws IOException {
        Path ini = tempDir.resolve("config.ini");
        Files.writeString(ini, String.join("\n",
                "codex.local.enable=true",
                "codex.local.model_name=codex:local-test",
                "codex.api.enable=true",
                "codex.api.model_name=codex:api-test"
        ));

        AppProperties appProperties = new AppProperties();
        appProperties.setDefaultModel("missing-default");

        CodexProperties codexProperties = new CodexProperties();
        codexProperties.getLocal().setConfigPath(ini.toString());
        codexProperties.getApi().setConfigPath(ini.toString());

        OllamaService ollamaService = new StubOllamaService(List.of("llama3:8b"));

        ModelCatalogService service = new ModelCatalogService(appProperties, ollamaService, codexProperties);
        service.refreshModels();

        assertEquals(List.of("llama3:8b", "codex:local-test", "codex:api-test"), service.getModels());
        assertEquals("llama3:8b", service.getDefaultModel());
    }

    @Test
    void refreshModels_usesFallbackWhenOllamaReturnsEmpty() throws IOException {
        Path ini = tempDir.resolve("config-disabled.ini");
        Files.writeString(ini, String.join("\n",
                "codex.local.enable=false",
                "codex.api.enable=false"
        ));

        AppProperties appProperties = new AppProperties();
        appProperties.setDefaultModel("mistral");
        appProperties.setModels(List.of("mistral", "qwen2.5"));

        CodexProperties codexProperties = new CodexProperties();
        codexProperties.getLocal().setConfigPath(ini.toString());
        codexProperties.getApi().setConfigPath(ini.toString());

        OllamaService ollamaService = new StubOllamaService(List.of());

        ModelCatalogService service = new ModelCatalogService(appProperties, ollamaService, codexProperties);
        service.refreshModels();

        assertEquals(List.of("mistral", "qwen2.5"), service.getModels());
        assertEquals("mistral", service.getDefaultModel());
        assertEquals("qwen2.5", service.resolveModel("qwen2.5"));
    }

    @Test
    void resolveModel_throwsForUnknownModel() {
        AppProperties appProperties = new AppProperties();
        appProperties.setDefaultModel("mistral");

        CodexProperties codexProperties = new CodexProperties();
        codexProperties.getLocal().setEnabled(false);
        codexProperties.getApi().setEnabled(false);

        OllamaService ollamaService = new StubOllamaService(List.of("mistral"));

        ModelCatalogService service = new ModelCatalogService(appProperties, ollamaService, codexProperties);
        service.refreshModels();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.resolveModel("unknown-model"));
        assertTrue(ex.getMessage().contains("Неизвестная модель"));
    }

    private static class StubOllamaService extends OllamaService {
        private final List<String> models;

        StubOllamaService(List<String> models) {
            super("http://localhost");
            this.models = models;
        }

        @Override
        public List<String> listLocalModels() {
            return models;
        }
    }
}
