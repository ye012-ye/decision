package com.ye.decision.tika.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tika 文本/元数据提取器配置，绑定 {@code decision.tika.*}。
 *
 * @author ye
 */
@Component
@ConfigurationProperties(prefix = "decision.tika")
public class TikaProperties {

    /** 单文档字符上限，-1 = 不限 */
    private int writeLimit = 10_000_000;

    /** 单文件最多解析的嵌入文档数 */
    private int maxEmbeddedResources = 200;

    /** 单文件解析超时 */
    private Duration parseTimeout = Duration.ofSeconds(60);

    /** MIME 白名单（baseType），为空 = 不限 */
    private Set<String> allowedMimeTypes = new LinkedHashSet<>();

    /** 解析线程池参数 */
    private Executor executor = new Executor();

    /**
     * 解析线程池配置。
     * <p>注意 {@code ThreadPoolExecutor} 的扩容语义：corePoolSize 满 → 先入队 →
     * 队列满才扩到 maxPoolSize。若希望高负载下更快用到 maxPoolSize，应调小 queueCapacity。
     */
    public static class Executor {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 100;

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    }

    public int getWriteLimit() { return writeLimit; }
    public void setWriteLimit(int writeLimit) { this.writeLimit = writeLimit; }
    public int getMaxEmbeddedResources() { return maxEmbeddedResources; }
    public void setMaxEmbeddedResources(int maxEmbeddedResources) { this.maxEmbeddedResources = maxEmbeddedResources; }
    public Duration getParseTimeout() { return parseTimeout; }
    public void setParseTimeout(Duration parseTimeout) { this.parseTimeout = parseTimeout; }
    public Set<String> getAllowedMimeTypes() { return allowedMimeTypes; }
    public void setAllowedMimeTypes(Set<String> allowedMimeTypes) { this.allowedMimeTypes = allowedMimeTypes; }
    public Executor getExecutor() { return executor; }
    public void setExecutor(Executor executor) { this.executor = executor; }
}
