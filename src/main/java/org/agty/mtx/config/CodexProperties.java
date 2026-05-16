package org.agty.mtx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "codex")
public class CodexProperties {

    private Local local = new Local();
    private Api api = new Api();

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public static class Local {
        private String configPath = "./config.ini";
        private boolean enabled = true;
        private String commandPath = "codexp";
        private String modelName = "codex:local";
        private int timeoutSeconds = 180;
        private boolean debug = false;
        private boolean bypassApprovals = false;
        private boolean keepSession = true;
        private String codeHomeDir = "./.codex-chat-provider";

        public String getConfigPath() {
            return configPath;
        }

        public void setConfigPath(String configPath) {
            this.configPath = configPath;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCommandPath() {
            return commandPath;
        }

        public void setCommandPath(String commandPath) {
            this.commandPath = commandPath;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isDebug() {
            return debug;
        }

        public void setDebug(boolean debug) {
            this.debug = debug;
        }

        public boolean isBypassApprovals() {
            return bypassApprovals;
        }

        public void setBypassApprovals(boolean bypassApprovals) {
            this.bypassApprovals = bypassApprovals;
        }

        public boolean isKeepSession() {
            return keepSession;
        }

        public void setKeepSession(boolean keepSession) {
            this.keepSession = keepSession;
        }

        public String getCodeHomeDir() {
            return codeHomeDir;
        }

        public void setCodeHomeDir(String codeHomeDir) {
            this.codeHomeDir = codeHomeDir;
        }
    }

    public static class Api {
        private boolean enabled = true;
        private String modelName = "codex:api";
        private String configPath = "./config.ini";
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String openaiModel = "gpt-5.5";
        private int timeoutSeconds = 120;
        private Proxy proxy = new Proxy();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getConfigPath() {
            return configPath;
        }

        public void setConfigPath(String configPath) {
            this.configPath = configPath;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getOpenaiModel() {
            return openaiModel;
        }

        public void setOpenaiModel(String openaiModel) {
            this.openaiModel = openaiModel;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public Proxy getProxy() {
            return proxy;
        }

        public void setProxy(Proxy proxy) {
            this.proxy = proxy;
        }
    }

    public static class Proxy {
        private boolean enabled = false;
        private String host = "";
        private int port = 8080;
        private String username = "";
        private String password = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
