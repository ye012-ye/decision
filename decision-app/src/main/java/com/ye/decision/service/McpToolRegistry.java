package com.ye.decision.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 维护从 MCP Server 动态发现的工具缓存。
 * <p>
 * 主应用启动时不要求 MCP 可用；后台定时尝试初始化并刷新工具列表。
 * <p>
 * 生命周期管理：本组件**自己构建** {@link McpSyncClient}，而不复用 Spring AI starter
 * 注入的 bean。这样在 SSE 连接掉线后可以 {@code closeGracefully()} 并在下一轮
 * 完全重新建链，实现真正的"自动恢复"。
 */
@Component
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final ClientFactory clientFactory;
    private final Duration retryBackoff;
    private final ReentrantLock refreshLock = new ReentrantLock();

    /** 活跃 client 映射：connection name → 已 initialize 的 client */
    private final Map<String, McpSyncClient> liveClients = new ConcurrentHashMap<>();

    private volatile List<ToolCallback> cachedToolCallbacks = List.of();
    private volatile Instant nextRetryAt = Instant.EPOCH;

    @Autowired
    public McpToolRegistry(McpSseClientProperties sseProperties,
                           @Value("${decision.mcp.retry-backoff-ms:5000}") long retryBackoffMs) {
        this(new SseClientFactory(sseProperties), Duration.ofMillis(30_000), Duration.ofMillis(retryBackoffMs));
    }

    /** 向后兼容：接受预构建的 client 列表（单测用，不会在失败时重建）。 */
    McpToolRegistry(List<McpSyncClient> mcpClients, Duration refreshInterval, Duration retryBackoff) {
        this(new StaticClientFactory(mcpClients), refreshInterval, retryBackoff);
    }

    private McpToolRegistry(ClientFactory clientFactory, Duration refreshInterval, Duration retryBackoff) {
        this.clientFactory = clientFactory;
        this.retryBackoff = retryBackoff;
    }

    public List<ToolCallback> getToolCallbacks() {
        return cachedToolCallbacks;
    }

    @Scheduled(
        initialDelayString = "${decision.mcp.initial-delay-ms:1000}",
        fixedDelayString = "${decision.mcp.refresh-interval-ms:30000}"
    )
    public void refreshScheduled() {
        refresh(false);
    }

    public void refreshNow() {
        refresh(true);
    }

    private void refresh(boolean force) {
        if (clientFactory.names().isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        if (!force && now.isBefore(nextRetryAt)) {
            return;
        }

        if (!refreshLock.tryLock()) {
            return;
        }

        try {
            refreshToolCallbacks();
        } finally {
            refreshLock.unlock();
        }
    }

    private void refreshToolCallbacks() {
        List<ToolCallback> refreshedCallbacks = new ArrayList<>();
        boolean hasFailure = false;

        for (String name : clientFactory.names()) {
            McpSyncClient client = liveClients.get(name);
            try {
                if (client == null) {
                    // 首次或上一轮被 discard —— 从 factory 构建一个全新 client
                    client = clientFactory.create(name);
                    if (client == null) {
                        continue;
                    }
                    if (!client.isInitialized()) {
                        client.initialize();
                    }
                    liveClients.put(name, client);
                } else {
                    // 已有实例 —— 用 ping 做真实的 liveness 探活（比 isInitialized 可靠）
                    client.ping();
                }

                ToolCallback[] toolCallbacks = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(List.of(client))
                    .toolNamePrefixGenerator(McpToolNamePrefixGenerator.noPrefix())
                    .build()
                    .getToolCallbacks();
                refreshedCallbacks.addAll(Arrays.asList(toolCallbacks));
            } catch (Exception e) {
                hasFailure = true;
                log.warn("MCP client '{}' unavailable, will rebuild next cycle: {}", name, e.getMessage());
                discardClient(name);
            }
        }

        cachedToolCallbacks = List.copyOf(refreshedCallbacks);
        nextRetryAt = hasFailure ? Instant.now().plus(retryBackoff) : Instant.EPOCH;
    }

    private void discardClient(String name) {
        McpSyncClient dead = liveClients.remove(name);
        if (dead == null) {
            return;
        }
        try {
            dead.closeGracefully();
        } catch (Exception e) {
            log.debug("Graceful close of dead MCP client '{}' failed: {}", name, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        for (String name : List.copyOf(liveClients.keySet())) {
            discardClient(name);
        }
    }

    // ── Client factory abstraction ───────────────────────────────────────────

    /** 抽象 client 的构建方式，方便单测塞入预构建实例。 */
    interface ClientFactory {
        Collection<String> names();
        McpSyncClient create(String name);
    }

    /** 生产实现：根据 SSE 配置每次构建全新的 McpSyncClient。 */
    private static final class SseClientFactory implements ClientFactory {
        private final McpSseClientProperties sseProperties;

        SseClientFactory(McpSseClientProperties sseProperties) {
            this.sseProperties = sseProperties;
        }

        @Override
        public Collection<String> names() {
            Map<String, McpSseClientProperties.SseParameters> conns = sseProperties.getConnections();
            return conns == null ? List.of() : conns.keySet();
        }

        @Override
        public McpSyncClient create(String name) {
            McpSseClientProperties.SseParameters params = sseProperties.getConnections().get(name);
            if (params == null) {
                return null;
            }
            HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(params.url());
            if (params.sseEndpoint() != null && !params.sseEndpoint().isBlank()) {
                builder.sseEndpoint(params.sseEndpoint());
            }
            return McpClient.sync(builder.build())
                .requestTimeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
        }
    }

    /** 测试实现：接受预构建 client，按插入顺序赋予稳定名称。 */
    private static final class StaticClientFactory implements ClientFactory {
        private final Map<String, McpSyncClient> clients;

        StaticClientFactory(List<McpSyncClient> mcpClients) {
            Map<String, McpSyncClient> map = new LinkedHashMap<>();
            for (int i = 0; i < mcpClients.size(); i++) {
                map.put("client-" + i, mcpClients.get(i));
            }
            this.clients = Map.copyOf(map);
        }

        @Override
        public Collection<String> names() {
            return clients.keySet();
        }

        @Override
        public McpSyncClient create(String name) {
            return clients.get(name);
        }
    }
}
