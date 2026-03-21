package com.ye.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ye.decision.feign.DownstreamClient;
import com.ye.decision.feign.OrderServiceClient;
import com.ye.decision.feign.UserServiceClient;
import com.ye.decision.tool.CallExternalApiTool;
import com.ye.decision.tool.QueryMysqlTool;
import com.ye.decision.tool.QueryRedisTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Configuration
public class ToolConfig {

    /**
     * 显式构造 Map，key 与 QueryMysqlReq.target 合法值对齐。
     * 不能依赖 Spring 自动注入 Map<String,DownstreamClient>，
     * 因为 Feign Bean 名称为 camelCase（"orderServiceClient"），与 "order-service" 不匹配。
     */
    @Bean
    public Map<String, DownstreamClient> downstreamClients(OrderServiceClient orderServiceClient,
                                                            UserServiceClient userServiceClient) {
        return Map.of(
            "order-service", orderServiceClient,
            "user-service", userServiceClient
        );
    }

    @Bean
    @Description("查询结构化业务数据，如订单、用户信息、交易记录、统计报表。适用于精确条件查询场景。")
    public QueryMysqlTool queryMysqlTool(Map<String, DownstreamClient> downstreamClients) {
        return new QueryMysqlTool(downstreamClients);
    }

    @Bean
    @Description("查询 Redis 中的缓存数据、热点数据、实时计数器、会话信息或排行榜。适用于低延迟、高频访问场景。")
    public QueryRedisTool queryRedisTool(RedisTemplate<String, Object> redisTemplate,
                                          ObjectMapper objectMapper) {
        return new QueryRedisTool(redisTemplate, objectMapper);
    }

    @Bean
    @Description("调用外部第三方服务，包括天气查询（weather）、物流追踪（logistics）、汇率查询（exchange-rate）。")
    public CallExternalApiTool callExternalApiTool(
        RestTemplate restTemplate,
        @Value("${decision.external.weather-url}") String weatherUrl,
        @Value("${decision.external.logistics-url}") String logisticsUrl,
        @Value("${decision.external.exchange-rate-url}") String exchangeRateUrl
    ) {
        return new CallExternalApiTool(restTemplate, weatherUrl, logisticsUrl, exchangeRateUrl);
    }
}
