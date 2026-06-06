package com.ye.decision;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

// 关闭 Kafka 自动配置（不再创建 KafkaTemplate/KafkaAdmin 等基础设施 Bean）
@SpringBootApplication(exclude = KafkaAutoConfiguration.class)
// 屏蔽整个 com.ye.decision.kafka 包：其下所有 @Component/@Configuration/@KafkaListener 都不参与扫描。
// 前两个 CUSTOM 过滤器是 @SpringBootApplication 默认带的，显式声明 @ComponentScan 后需手动保留。
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ye\\.decision\\.kafka\\..*")
})
@EnableFeignClients(basePackages = "com.ye.decision.feign")
@MapperScan({"com.ye.decision.mapper", "com.ye.decision.rag.mapper"})
@EnableScheduling
@EnableConfigurationProperties
public class DecisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionApplication.class, args);
    }

}
