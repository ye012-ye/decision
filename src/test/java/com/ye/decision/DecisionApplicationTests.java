package com.ye.decision;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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
    "decision.external.exchange-rate-url=http://exchange.test/rate"
})
class DecisionApplicationTests {

    @Test
    void contextLoads() {
    }

}
