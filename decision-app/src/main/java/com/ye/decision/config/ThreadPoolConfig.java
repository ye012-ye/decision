package com.ye.decision.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 公共线程池配置。
 *
 * @author Administrator
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * SSE 推流线程池：核心10，最大200，队列1000，拒绝时由调用方线程执行。
     */
    @Bean
    public ExecutorService sseExecutor() {
        return new ThreadPoolExecutor(
            10, 200,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("sse-pool-" + t.getId());
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
