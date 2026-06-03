package com.ye.decision.tika.domain;

import org.apache.tika.parser.PasswordProvider;

import java.time.Duration;
import java.util.Set;

/** 提取器配置。用 builder 装配，build 出来即不可变。 */
public record ExtractorConfig(
        int writeLimit,              // 单文档字符上限，-1 = 不限
        int maxEmbeddedResources,    // 最多解多少个嵌入文档
        Duration parseTimeout,       // 单文件解析超时
        Set<String> allowedMimeTypes,// MIME 白名单（baseType），空 = 不限
        PasswordProvider passwordProvider) { // 加密文档取密码，可为 null

    public ExtractorConfig {
        if (parseTimeout == null || parseTimeout.isZero() || parseTimeout.isNegative()) {
            throw new IllegalArgumentException("parseTimeout 必须为正");
        }
        allowedMimeTypes = allowedMimeTypes == null ? Set.of() : Set.copyOf(allowedMimeTypes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int writeLimit = 10_000_000;            // 1000 万字符
        private int maxEmbeddedResources = 200;
        private Duration parseTimeout = Duration.ofSeconds(60);
        private Set<String> allowedMimeTypes = Set.of();
        private PasswordProvider passwordProvider = null;

        public Builder writeLimit(int v)            { this.writeLimit = v; return this; }
        public Builder maxEmbeddedResources(int v)  { this.maxEmbeddedResources = v; return this; }
        public Builder parseTimeout(Duration v)     { this.parseTimeout = v; return this; }
        public Builder allowedMimeTypes(Set<String> v) { this.allowedMimeTypes = v; return this; }
        public Builder passwordProvider(PasswordProvider v) { this.passwordProvider = v; return this; }

        public ExtractorConfig build() {
            return new ExtractorConfig(writeLimit, maxEmbeddedResources,
                    parseTimeout, allowedMimeTypes, passwordProvider);
        }
    }
}