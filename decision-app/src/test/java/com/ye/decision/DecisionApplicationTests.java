package com.ye.decision;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.ai.dashscope.api-key=test-key",
    "spring.data.redis.host=127.0.0.1",
    "spring.data.redis.port=6379",
    "decision.external.weather-url=http://weather.test/current",
    "decision.external.logistics-url=http://logistics.test/track",
    "decision.external.exchange-rate-url=http://exchange.test/rate",
    // H2 内存库代替 MySQL
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.mode=always",
    // 禁用 RabbitMQ 自动配置（用 MockBean 替代）
    "spring.rabbitmq.host=localhost",
    "spring.rabbitmq.port=5672"
})
class DecisionApplicationTests {

    // Redisson 启动时立即连接 Redis — 用 mock 避免测试依赖真实 Redis
    @MockBean
    RedissonClient redissonClient;

    // RabbitMQ AMQP beans — 用 mock 避免测试依赖真实 Broker
    @MockBean
    AmqpAdmin amqpAdmin;

    @MockBean
    RabbitTemplate rabbitTemplate;

    @Test
    void contextLoads() {
    }

}
