package com.ye.decision.controller;

import com.ye.decision.domain.entity.SysUser;
import com.ye.decision.mapper.SysUserMapper;
import com.ye.decision.security.JwtService;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.ai.dashscope.api-key=test-key",
    "spring.data.redis.host=127.0.0.1",
    "spring.data.redis.port=6379",
    "decision.external.weather-url=http://weather.test/current",
    "decision.external.logistics-url=http://logistics.test/track",
    "decision.external.exchange-rate-url=http://exchange.test/rate",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.mode=always",
    "spring.rabbitmq.host=localhost",
    "spring.rabbitmq.port=5672",
    "decision.security.jwt.secret=test-secret-test-secret-test-secret-123456",
    "decision.security.jwt.expire-minutes=120",
    "decision.rag.milvus.uri=http://localhost:9999"
})
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @MockBean
    SysUserMapper userMapper;

    @MockBean
    RedissonClient redissonClient;

    @MockBean
    AmqpAdmin amqpAdmin;

    @MockBean
    RabbitTemplate rabbitTemplate;

    @MockBean
    MilvusClientV2 milvusClientV2;

    private SysUser activeAdmin() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword(new BCryptPasswordEncoder().encode("admin123"));
        u.setNickname("管理员");
        u.setRole("ADMIN");
        u.setStatus(1);
        return u;
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(activeAdmin());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.nickname").value("管理员"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(activeAdmin());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void me_withValidToken_returnsCurrentUser() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(activeAdmin());
        String token = jwtService.generateToken("admin");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.nickname").value("管理员"))
            .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }
}
