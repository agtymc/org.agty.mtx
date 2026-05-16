package org.agty.mtx.service;

import org.agty.mtx.config.CodexProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CodexCliService {
    private static final Logger log = LoggerFactory.getLogger(CodexCliService.class);
    private static final int LOG_TAIL_LIMIT = 4000;

    private final CodexProperties codexProperties;
    private final String workspaceDir;

    public CodexCliService(CodexProperties codexProperties, @Value("${user.dir}") String workspaceDir) {
        this.codexProperties = codexProperties;
        this.workspaceDir = workspaceDir;
    }

    public boolean isCodexModel(String model) {
        LocalSettings settings = resolveLocalSettings();
        if (!settings.enabled()) {
            return false;
        }
        String normalizedModel = normalize(model);
        String codexModel = normalize(settings.modelName());
        return !codexModel.isBlank() && codexModel.equals(normalizedModel);
    }

    public String chat(List<Map<String, String>> history) {
        LocalSettings settings = resolveLocalSettings();
        String prompt = buildPrompt(history);
        if (settings.keepSession()) {
            try {
                return runCodex(prompt, true, settings);
            } catch (Exception ex) {
                if (settings.debug()) {
                    log.warn("Codex resume failed, fallback to fresh exec: {}", ex.getMessage());
                }
                return runCodex(prompt, false, settings);
            }
        }
        return runCodex(prompt, false, settings);
    }

    private String runCodex(String prompt, boolean resumeLast, LocalSettings settings) {
        List<String> command = new ArrayList<>();
        command.add(settings.commandPath());
        command.add("exec");
        if (resumeLast) {
            command.add("resume");
            command.add("--last");
        }
        command.add("--skip-git-repo-check");
        if (!resumeLast) {
            command.add("--sandbox");
            command.add("read-only");
        }
        if (settings.bypassApprovals()) {
            command.add("--dangerously-bypass-approvals-and-sandbox");
        }
        if (!resumeLast) {
            command.add("-C");
            command.add(workspaceDir);
        }

        Path outputFile = null;
        Path stdioFile = null;
        try {
            outputFile = Files.createTempFile("codex-chat-reply-", ".txt");
            stdioFile = Files.createTempFile("codex-chat-stdio-", ".log");
            command.add("--output-last-message");
            command.add(outputFile.toAbsolutePath().toString());
            command.add(prompt);

            if (settings.debug()) {
                log.info("Codex start: mode={}, path={}, timeout={}s, bypassApprovals={}, workspace={}, codexHome={}",
                        resumeLast ? "resume-last" : "exec",
                        settings.commandPath(),
                        settings.timeoutSeconds(),
                        settings.bypassApprovals(),
                        workspaceDir,
                        settings.codeHomeDir());
                log.debug("Codex command: {}", command);
            }

            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(Path.of(workspaceDir).toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(stdioFile.toFile());
            Path codexHome = resolveCodexHomeDir(settings);
            Files.createDirectories(codexHome);
            pb.environment().put("CODEX_HOME", codexHome.toString());
            Process process = pb.start();
            process.getOutputStream().close();
            if (settings.debug()) {
                log.info("Codex process started: pid={}", process.pid());
            }

            if (settings.debug()) {
                log.debug("Codex waiting for completion...");
            }
            boolean finished = process.waitFor(Math.max(5, settings.timeoutSeconds()), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                if (settings.debug()) {
                    log.warn("Codex timeout. Stdio tail:\n{}", tailFile(stdioFile));
                }
                throw new IllegalStateException("Превышен таймаут ответа Codex (" + settings.timeoutSeconds() + "s)");
            }

            int exitCode = process.exitValue();
            String output = readOutput(outputFile);
            String stdioTail = tailFile(stdioFile);

            if (settings.debug()) {
                log.info("Codex finished: exitCode={}, outputChars={}", exitCode, output.length());
                if (!stdioTail.isBlank()) {
                    log.debug("Codex stdio tail:\n{}", stdioTail);
                }
            }

            if (!output.isBlank()) {
                return output;
            }
            if (exitCode != 0) {
                throw new IllegalStateException("Codex завершился с кодом " + exitCode + (stdioTail.isBlank() ? "" : ": " + stdioTail));
            }
            if (!stdioTail.isBlank()) {
                return stdioTail;
            }
            throw new IllegalStateException("Codex вернул пустой ответ");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (settings.debug()) {
                log.error("Codex launch error: {}", ex.getMessage(), ex);
            }
            throw new IllegalStateException("Ошибка запуска Codex CLI: " + ex.getMessage(), ex);
        } finally {
            deleteQuietly(outputFile);
            deleteQuietly(stdioFile);
        }
    }

    private Path resolveCodexHomeDir(LocalSettings settings) {
        String raw = settings.codeHomeDir();
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return Paths.get(workspaceDir, ".codex-chat-provider").toAbsolutePath().normalize();
        }
        Path path = Paths.get(value);
        if (!path.isAbsolute()) {
            path = Paths.get(workspaceDir).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private String readOutput(Path outputFile) {
        if (outputFile == null || !Files.exists(outputFile)) {
            return "";
        }
        try {
            return Files.readString(outputFile).trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    private String tailFile(Path file) {
        if (file == null || !Files.exists(file)) {
            return "";
        }
        try {
            String content = Files.readString(file);
            if (content.length() <= LOG_TAIL_LIMIT) {
                return content.trim();
            }
            return content.substring(content.length() - LOG_TAIL_LIMIT).trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private String buildPrompt(List<Map<String, String>> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Ниже история диалога. Ответь как ассистент на последнее сообщение пользователя.\n")
                .append("Не добавляй служебные пояснения, дай только полезный ответ.\n\n");

        if (history != null) {
            for (Map<String, String> item : history) {
                if (item == null) {
                    continue;
                }
                String role = normalize(item.get("role"));
                String content = item.get("content") == null ? "" : item.get("content").trim();
                if (content.isBlank()) {
                    continue;
                }
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }
                prompt.append("[").append(role).append("]\n");
                prompt.append(content).append("\n\n");
            }
        }

        return prompt.toString().trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private LocalSettings resolveLocalSettings() {
        LocalSettings base = new LocalSettings(
                codexProperties.getLocal().isEnabled(),
                safe(codexProperties.getLocal().getCommandPath(), "codexp"),
                safe(codexProperties.getLocal().getModelName(), "codex:local"),
                Math.max(5, codexProperties.getLocal().getTimeoutSeconds()),
                codexProperties.getLocal().isDebug(),
                codexProperties.getLocal().isBypassApprovals(),
                codexProperties.getLocal().isKeepSession(),
                safe(codexProperties.getLocal().getCodeHomeDir(), "./.codex-chat-provider")
        );
        Map<String, String> ini = readIniConfig();
        return new LocalSettings(
                parseBooleanOrDefault(ini.get("codex.local.enable"), base.enabled()),
                pick(ini.get("codex.local.command_path"), base.commandPath()),
                pick(ini.get("codex.local.model_name"), base.modelName()),
                parseIntOrDefault(ini.get("codex.local.timeout_seconds"), base.timeoutSeconds()),
                parseBooleanOrDefault(ini.get("codex.local.debug"), base.debug()),
                parseBooleanOrDefault(ini.get("codex.local.bypass_approvals"), base.bypassApprovals()),
                parseBooleanOrDefault(ini.get("codex.local.keep_session"), base.keepSession()),
                pick(ini.get("codex.local.code_home_dir"), base.codeHomeDir())
        );
    }

    private Map<String, String> readIniConfig() {
        Map<String, String> values = new HashMap<>();
        String configPathRaw = codexProperties.getLocal().getConfigPath();
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
            log.warn("Cannot read codex local ini config {}: {}", configPath, ex.getMessage());
        }
        return values;
    }

    private String safe(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private String pick(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty() ? fallback : preferred.trim();
    }

    private int parseIntOrDefault(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
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

    private record LocalSettings(
            boolean enabled,
            String commandPath,
            String modelName,
            int timeoutSeconds,
            boolean debug,
            boolean bypassApprovals,
            boolean keepSession,
            String codeHomeDir
    ) {
    }
}
