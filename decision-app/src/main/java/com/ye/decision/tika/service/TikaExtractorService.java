package com.ye.decision.tika.service;

import com.ye.decision.tika.domain.DocSegment;
import com.ye.decision.tika.domain.ExtractResult;
import com.ye.decision.tika.domain.ExtractorConfig;
import lombok.RequiredArgsConstructor;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.*;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ye.decision.tika.domain.ExtractResult.Status.*;


/**
 * 生产级通用文本/元数据提取器。
 * <p>线程安全：{@link AutoDetectParser} 与本类的方法可被多线程并发调用。
 */
@Service
@RequiredArgsConstructor
public final class TikaExtractorService {

    private static final Logger log = LoggerFactory.getLogger(TikaExtractorService.class);
    private static final Parser parser = new AutoDetectParser();   // 线程安全，全局复用
    private static final Detector detector = TikaConfig.getDefaultConfig().getDetector();
    private final ExtractorConfig config;            // 由 decision.tika.* 装配，构造器注入
    private final ThreadPoolTaskExecutor tikaExecutor;


    /** 从文件抽取。坏文件不抛异常，返回 FAILED 状态——批处理友好。 */
    public ExtractResult extract(Path file) {
        try (TikaInputStream tis = TikaInputStream.get(file)) {   // 文件流，解析器可重读
            return doExtract(tis, file.getFileName().toString());
        } catch (IOException e) {
            log.warn("打开文件失败: {}", file, e);
            return new ExtractResult(FAILED, null, null, Map.of(), false, e.toString());
        }
    }


    /** 从任意输入流抽取（name 仅用于日志/元数据，可为文件名）。 */
    public ExtractResult extract(InputStream in, String name) {
        try (TikaInputStream tis = TikaInputStream.get(in)) {     // 必要时落临时盘并自动清理
            return doExtract(tis, name);
        } catch (IOException e) {
            log.warn("读取流失败: {}", name, e);
            return new ExtractResult(FAILED, null, null, Map.of(), false, e.toString());
        }
    }

    /** 递归抽取文件 */
    public List<DocSegment> extractRecursive(Path file) {
        Parser recursive = new RecursiveParserWrapper(parser);
        var factory = new BasicContentHandlerFactory(
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, config.writeLimit());
        var handler = new RecursiveParserWrapperHandler(factory, config.maxEmbeddedResources());

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getFileName().toString());

        ParseContext ctx = new ParseContext();
        ctx.set(Parser.class, recursive);
        if (config.passwordProvider() != null) {
            ctx.set(PasswordProvider.class, config.passwordProvider());
        }

        // 同步等待解析完成（在流关闭前 join）；超时则取消任务，已解出的嵌入文档仍在 metadataList 里
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            Future<?> future = tikaExecutor.submit(() -> {
                recursive.parse(tis, handler, metadata, ctx);
                return null;
            });
            try {
                future.get(config.parseTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                log.info("递归解析超时: {}", file);
            }
        } catch (Exception e) {
            log.warn("递归解析失败: {}", file, e);   // 已解出的部分仍在 metadataList 里
        }

        return handler.getMetadataList().stream()
                .map(m -> new DocSegment(
                        m.get(TikaCoreProperties.RESOURCE_NAME_KEY),
                        m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH),
                        m.get(Metadata.CONTENT_TYPE),
                        Optional.ofNullable(m.get(TikaCoreProperties.TIKA_CONTENT)).orElse("")))
                .toList();
    }

    // —— 真正干活的地方 ——
    private ExtractResult doExtract(TikaInputStream tis, String name) throws IOException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);

        // 1) 先验真 MIME（不信客户端），Detector 用 mark/reset，不消费流
        MediaType detected = detector.detect(tis, metadata);
        String baseType = detected.getBaseType().toString();
        if (!config.allowedMimeTypes().isEmpty()
                && !config.allowedMimeTypes().contains(baseType)) {
            return new ExtractResult(SKIPPED_MIME, "", baseType, Map.of(), false,
                    "MIME 不在白名单: " + baseType);
        }
        metadata.set(Metadata.CONTENT_TYPE, detected.toString()); // 复用检测结果，省一次

        // 2) BodyContentHandler 会自动把"嵌入文档的正文"也拼进来（因 ctx 里设了 Parser）
        BodyContentHandler handler = new BodyContentHandler(config.writeLimit());
        ParseContext ctx = buildContext();

        // 3) 丢进独立线程跑，套超时
        Future<?> future = tikaExecutor.submit(() -> {
            parser.parse(tis, handler, metadata, ctx);
            return null;
        });

        boolean truncated = false;
        try {
            future.get(config.parseTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.info("解析超时: {}", name);
            // 不回读 handler：后台线程可能仍在写这个非线程安全对象，且超时正文本就不完整
            return new ExtractResult(TIMEOUT, "", baseType,
                    toMap(metadata), true, "超过 " + config.parseTimeout());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return new ExtractResult(FAILED, null, baseType, toMap(metadata), false, "被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (isWriteLimitReached(cause)) {
                truncated = true;   // 截断不是错：handler 里是截断版正文，metadata 仍可信
            } else if (cause instanceof EncryptedDocumentException) {
                return new ExtractResult(ENCRYPTED, "", baseType, toMap(metadata), false, "加密文档");
            } else {
                log.warn("解析失败: {}", name, cause);   // 损坏/不支持/POI/PDFBox 异常
                return new ExtractResult(FAILED, "", baseType, toMap(metadata), false,
                        String.valueOf(cause));
            }
        }
        return new ExtractResult(OK, handler.toString(), baseType, toMap(metadata), truncated, null);
    }

    private ParseContext buildContext() {
        ParseContext ctx = new ParseContext();
        ctx.set(Parser.class, parser);   // 关键：嵌入文档（附件/压缩包/内嵌office）才会被递归解析
        PasswordProvider pp = config.passwordProvider();
        if (pp != null) {
            ctx.set(PasswordProvider.class, pp);
        }
        return ctx;
    }

    private static Map<String, String> toMap(Metadata m) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : m.names()) {
            out.put(name, m.get(name));
        }
        return out;
    }

    /** 写入上限既可能直接抛，也可能被包成 SAXException 的 cause——两种都认。 */
    private static boolean isWriteLimitReached(Throwable t) {
        while (t != null) {
            if (t instanceof WriteLimitReachedException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

}