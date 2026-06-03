package com.ye.decision.tika.service;

import com.ye.decision.tika.domain.DocSegment;
import com.ye.decision.tika.domain.ExtractResult;
import com.ye.decision.tika.domain.ExtractorConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link TikaExtractorService} 单元测试——纯本地，不启动 Spring 上下文。 */
class TikaExtractorServiceTest {

    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setDaemon(true);
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    private TikaExtractorService service(ExtractorConfig config) {
        return new TikaExtractorService(config, executor);
    }

    private ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("纯文本抽取成功，状态 OK 且正文可信")
    void plainText_extractsOk() {
        ExtractResult r = service(ExtractorConfig.builder().build())
                .extract(stream("hello tika world"), "note.txt");

        assertThat(r.ok()).isTrue();
        assertThat(r.status()).isEqualTo(ExtractResult.Status.OK);
        assertThat(r.text()).contains("hello tika world");
        assertThat(r.contentType()).startsWith("text/plain");
        assertThat(r.truncated()).isFalse();
    }

    @Test
    @DisplayName("超过 writeLimit 时标记为截断，状态仍为 OK")
    void writeLimitExceeded_marksTruncated() {
        ExtractResult r = service(ExtractorConfig.builder().writeLimit(5).build())
                .extract(stream("0123456789abcdefghij"), "long.txt");

        assertThat(r.status()).isEqualTo(ExtractResult.Status.OK);
        assertThat(r.truncated()).isTrue();
    }

    @Test
    @DisplayName("MIME 不在白名单时跳过抽取")
    void mimeNotInWhitelist_skipped() {
        ExtractResult r = service(ExtractorConfig.builder()
                .allowedMimeTypes(Set.of("application/pdf"))
                .build())
                .extract(stream("just text"), "note.txt");

        assertThat(r.status()).isEqualTo(ExtractResult.Status.SKIPPED_MIME);
        assertThat(r.ok()).isFalse();
        assertThat(r.message()).contains("白名单");
    }

    @Test
    @DisplayName("文件无法打开时返回 FAILED，不抛异常")
    void unreadableFile_returnsFailed() {
        ExtractResult r = service(ExtractorConfig.builder().build())
                .extract(Path.of("does-not-exist-12345.bin"));

        assertThat(r.status()).isEqualTo(ExtractResult.Status.FAILED);
        assertThat(r.ok()).isFalse();
    }

    @Test
    @DisplayName("递归抽取返回顶层文档分段")
    void recursiveExtract_returnsSegments() throws Exception {
        Path tmp = Files.createTempFile("tika-test", ".txt");
        Files.writeString(tmp, "recursive content here");
        try {
            List<DocSegment> segments = service(ExtractorConfig.builder().build())
                    .extractRecursive(tmp);

            assertThat(segments).isNotEmpty();
            assertThat(segments.get(0).text()).contains("recursive content");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
