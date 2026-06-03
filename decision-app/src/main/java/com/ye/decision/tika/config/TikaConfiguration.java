package com.ye.decision.tika.config;

import com.ye.decision.tika.domain.ExtractorConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TikaConfiguration {

    /** 由 {@code decision.tika.*} 装配出的不可变提取器配置。 */
    @Bean
    public ExtractorConfig extractorConfig(TikaProperties props) {
        return ExtractorConfig.builder()
                .writeLimit(props.getWriteLimit())
                .maxEmbeddedResources(props.getMaxEmbeddedResources())
                .parseTimeout(props.getParseTimeout())
                .allowedMimeTypes(props.getAllowedMimeTypes())
                .build();
    }

    @Bean
    public ThreadPoolTaskExecutor tikaExecutor(TikaProperties props) {
        TikaProperties.Executor cfg = props.getExecutor();
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(cfg.getCorePoolSize());
        e.setMaxPoolSize(cfg.getMaxPoolSize());
        e.setQueueCapacity(cfg.getQueueCapacity());
        e.setThreadNamePrefix("tika-executor-");
        e.setDaemon(true);
        e.initialize();
        return e;
    }
}
