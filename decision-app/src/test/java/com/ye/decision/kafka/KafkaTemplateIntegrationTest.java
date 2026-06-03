package com.ye.decision.kafka;

import com.ye.decision.kafka.config.KafkaConsumerConfig;
import com.ye.decision.kafka.config.KafkaProducerConfig;
import com.ye.decision.kafka.consumer.InMemoryIdempotencyChecker;
import com.ye.decision.kafka.core.KafkaMessage;
import com.ye.decision.kafka.core.KafkaProducerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端集成测试（EmbeddedKafka）：验证“生产 -> 消费”链路。
 *
 * <p>本测试需要 {@code spring-kafka-test} 依赖，且应放在一个可构建的 Maven 模块中运行。
 * 模板目录本身不参与构建，此文件作为可直接复用的参考。</p>
 */
@SpringBootTest(classes = KafkaTemplateIntegrationTest.TestApp.class)
@EmbeddedKafka(partitions = 1, topics = {"demo-topic", "demo-topic.DLT"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.kafka.topic=demo-topic",
        "app.kafka.consumer-group=test-group",
        "app.kafka.concurrency=1"
})
@DirtiesContext
class KafkaTemplateIntegrationTest {

    @Autowired
    private KafkaProducerService producerService;

    @Autowired
    private TestListener testListener;

    @Test
    void producedMessageIsConsumed() throws Exception {
        KafkaMessage msg = KafkaMessage.of("TEST_EVENT", "hello");

        producerService.sendSync("demo-topic", msg.getMessageId(), msg);

        KafkaMessage received = testListener.received.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getEventType()).isEqualTo("TEST_EVENT");
        assertThat(received.getPayload()).isEqualTo("hello");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({KafkaProducerConfig.class, KafkaConsumerConfig.class,
            KafkaProducerService.class, InMemoryIdempotencyChecker.class, TestListener.class})
    static class TestApp {
    }

    @Component
    static class TestListener {
        final BlockingQueue<KafkaMessage> received = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "demo-topic", groupId = "test-group",
                containerFactory = "kafkaListenerContainerFactory")
        void listen(ConsumerRecord<String, KafkaMessage> record) {
            received.add(record.value());
        }
    }
}
