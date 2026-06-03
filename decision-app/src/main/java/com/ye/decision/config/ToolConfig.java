package com.ye.decision.config;

import com.ye.decision.feign.DownstreamClient;
import com.ye.decision.feign.OrderServiceClient;
import com.ye.decision.feign.UserServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
