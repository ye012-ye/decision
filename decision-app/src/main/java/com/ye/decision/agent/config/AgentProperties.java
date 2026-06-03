package com.ye.decision.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "decision.agent")
public class AgentProperties {

    private int memoryWindowSize = 20;
    private Router router = new Router();
    private Limit modelCallLimit = new Limit(8);
    private Limit toolCallLimit = new Limit(12);
    private Tools tools = new Tools();
    private Skills skills = new Skills();

    public int getMemoryWindowSize() {
        return memoryWindowSize;
    }

    public void setMemoryWindowSize(int memoryWindowSize) {
        this.memoryWindowSize = memoryWindowSize;
    }

    public Router getRouter() {
        return router;
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    public Limit getModelCallLimit() {
        return modelCallLimit;
    }

    public void setModelCallLimit(Limit modelCallLimit) {
        this.modelCallLimit = modelCallLimit;
    }

    public Limit getToolCallLimit() {
        return toolCallLimit;
    }

    public void setToolCallLimit(Limit toolCallLimit) {
        this.toolCallLimit = toolCallLimit;
    }

    public Tools getTools() {
        return tools;
    }

    public void setTools(Tools tools) {
        this.tools = tools;
    }

    public Skills getSkills() {
        return skills;
    }

    public void setSkills(Skills skills) {
        this.skills = skills;
    }

    public static class Router {
        private String fallbackAgent = "chat";

        public String getFallbackAgent() {
            return fallbackAgent;
        }

        public void setFallbackAgent(String fallbackAgent) {
            this.fallbackAgent = fallbackAgent;
        }
    }

    public static class Limit {
        private int runLimit;

        public Limit() {
        }

        public Limit(int runLimit) {
            this.runLimit = runLimit;
        }

        public int getRunLimit() {
            return runLimit;
        }

        public void setRunLimit(int runLimit) {
            this.runLimit = runLimit;
        }
    }

    public static class Tools {
        private boolean parallelExecution = true;
        private int maxParallelTools = 4;
        private Duration executionTimeout = Duration.ofSeconds(30);
        private boolean wrapSyncToolsAsAsync = true;
        private int maxRetries = 1;

        public boolean isParallelExecution() {
            return parallelExecution;
        }

        public void setParallelExecution(boolean parallelExecution) {
            this.parallelExecution = parallelExecution;
        }

        public int getMaxParallelTools() {
            return maxParallelTools;
        }

        public void setMaxParallelTools(int maxParallelTools) {
            this.maxParallelTools = maxParallelTools;
        }

        public Duration getExecutionTimeout() {
            return executionTimeout;
        }

        public void setExecutionTimeout(Duration executionTimeout) {
            this.executionTimeout = executionTimeout;
        }

        public boolean isWrapSyncToolsAsAsync() {
            return wrapSyncToolsAsAsync;
        }

        public void setWrapSyncToolsAsAsync(boolean wrapSyncToolsAsAsync) {
            this.wrapSyncToolsAsAsync = wrapSyncToolsAsAsync;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    public static class Skills {
        private String mode = "classpath";
        private String classpathPath = "skills";
        private String basePath = System.getProperty("java.io.tmpdir");
        private boolean autoReload = false;
        private boolean failFast = true;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getClasspathPath() {
            return classpathPath;
        }

        public void setClasspathPath(String classpathPath) {
            this.classpathPath = classpathPath;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public boolean isAutoReload() {
            return autoReload;
        }

        public void setAutoReload(boolean autoReload) {
            this.autoReload = autoReload;
        }

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }
    }
}
