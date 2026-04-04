package com.ye.decision.rag;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.decision.rag.domain.dto.DocumentUploadResp;
import com.ye.decision.rag.domain.dto.KnowledgeBaseReq;
import com.ye.decision.rag.domain.dto.KnowledgeBaseVO;
import com.ye.decision.rag.domain.dto.KnowledgeDocumentVO;
import com.ye.decision.rag.domain.entity.KnowledgeDocumentEntity;
import com.ye.decision.rag.domain.enums.DocumentStatus;
import com.ye.decision.rag.exception.RagErrorCode;
import com.ye.decision.rag.exception.RagException;
import com.ye.decision.rag.search.HybridSearchService;
import com.ye.decision.rag.search.SearchResult;
import com.ye.decision.rag.service.DocumentIngestionService;
import com.ye.decision.rag.service.KnowledgeBaseService;
import com.ye.decision.rag.service.KnowledgeDocumentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 模块集成测试。
 * <p>
 * 连接真实基础设施：MySQL、RabbitMQ、Milvus、DashScope Embedding。
 * 测试完整的 知识库CRUD → 文档上传 → 摄入 → 混合检索 链路。
 * <p>
 * 运行前确保 192.168.83.128 上的所有服务可达。
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class RagIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RagIntegrationTest.class);

    /** 测试专用知识库编码，测试结束后会清理 */
    private static final String TEST_KB_CODE = "test_integration_kb";
    private static String testDocId;
    private static boolean ingestionDone = false;

    @Autowired KnowledgeBaseService kbService;
    @Autowired KnowledgeDocumentService docService;
    @Autowired DocumentIngestionService ingestionService;
    @Autowired HybridSearchService hybridSearchService;

    // ══════════════════════════════════════════════════════════
    //  1. 知识库 CRUD
    // ══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("1.1 创建知识库")
    void createKnowledgeBase() {
        // 清理可能残留的测试数据
        try { kbService.delete(TEST_KB_CODE); } catch (Exception ignored) {}

        KnowledgeBaseReq req = new KnowledgeBaseReq(
            TEST_KB_CODE, "集成测试知识库", "用于自动化集成测试", "test_user");
        KnowledgeBaseVO vo = kbService.create(req);

        assertEquals(TEST_KB_CODE, vo.kbCode());
        assertEquals("集成测试知识库", vo.kbName());
        assertNotNull(vo.createdAt());
        log.info("知识库创建成功: {}", vo.kbCode());
    }

    @Test
    @Order(2)
    @DisplayName("1.2 重复创建知识库 → 抛异常")
    void createDuplicateKb_throwsException() {
        KnowledgeBaseReq req = new KnowledgeBaseReq(
            TEST_KB_CODE, "重复", null, "test_user");
        RagException ex = assertThrows(RagException.class, () -> kbService.create(req));
        assertEquals(RagErrorCode.KB_CODE_DUPLICATE, ex.getErrorCode());
    }

    @Test
    @Order(3)
    @DisplayName("1.3 查询知识库列表")
    void listKnowledgeBases() {
        List<KnowledgeBaseVO> list = kbService.listAll();
        assertTrue(list.stream().anyMatch(kb -> TEST_KB_CODE.equals(kb.kbCode())),
            "列表中应包含测试知识库");
    }

    @Test
    @Order(4)
    @DisplayName("1.4 更新知识库")
    void updateKnowledgeBase() {
        KnowledgeBaseReq req = new KnowledgeBaseReq(
            TEST_KB_CODE, "集成测试知识库-已更新", "更新后的描述", "test_user");
        KnowledgeBaseVO vo = kbService.update(TEST_KB_CODE, req);
        assertEquals("集成测试知识库-已更新", vo.kbName());
    }

    // ══════════════════════════════════════════════════════════
    //  2. 文档上传
    // ══════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("2.1 上传 txt 文档")
    void uploadDocument() throws IOException {
        String content = """
            Spring AI 是一个用于构建 AI 应用的 Java 框架。
            它支持多种大语言模型，包括 OpenAI、DashScope、Ollama 等。
            Spring AI 提供了统一的 ChatModel 接口，简化了模型切换。
            向量存储支持 Milvus、Qdrant、Redis 等多种后端。
            RAG（检索增强生成）是 Spring AI 的核心能力之一，
            通过将外部知识注入到 LLM 的上下文中，提升回答的准确性。
            文档切片使用 TokenTextSplitter，按 token 粒度切分。
            混合检索结合了稠密向量的语义匹配和 BM25 的关键词匹配。
            RRF（Reciprocal Rank Fusion）算法用于融合多路检索结果。
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file", "spring-ai-intro.txt", "text/plain",
            content.getBytes(StandardCharsets.UTF_8));

        DocumentUploadResp resp = docService.upload(TEST_KB_CODE, file, "test_user");

        assertNotNull(resp.docId());
        assertEquals("spring-ai-intro.txt", resp.fileName());
        assertEquals("PENDING", resp.status());
        testDocId = resp.docId();
        log.info("文档上传成功: docId={}", testDocId);
    }

    @Test
    @Order(11)
    @DisplayName("2.2 上传空文件 → 抛异常")
    void uploadEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.txt", "text/plain", new byte[0]);
        RagException ex = assertThrows(RagException.class,
            () -> docService.upload(TEST_KB_CODE, file, "test_user"));
        assertEquals(RagErrorCode.DOC_FILE_EMPTY, ex.getErrorCode());
    }

    @Test
    @Order(12)
    @DisplayName("2.3 上传不支持的文件类型 → 抛异常")
    void uploadUnsupportedType() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "virus.exe", "application/octet-stream", "data".getBytes());
        RagException ex = assertThrows(RagException.class,
            () -> docService.upload(TEST_KB_CODE, file, "test_user"));
        assertEquals(RagErrorCode.DOC_FILE_TYPE_UNSUPPORTED, ex.getErrorCode());
    }

    @Test
    @Order(13)
    @DisplayName("2.4 查询文档列表")
    void listDocuments() {
        Assumptions.assumeTrue(testDocId != null, "跳过：上传测试未成功");
        Page<KnowledgeDocumentVO> page = docService.listByKbCode(TEST_KB_CODE, 1, 20);
        assertTrue(page.getRecords().size() > 0, "文档列表不应为空");
        log.info("文档列表: records={}, total={}", page.getRecords().size(), page.getTotal());
    }

    // ══════════════════════════════════════════════════════════
    //  3. 文档摄入（同步调用，跳过 MQ）
    // ══════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("3.1 同步执行文档摄入")
    void ingestDocument() {
        Assumptions.assumeTrue(testDocId != null, "跳过：上传测试未成功");

        // 从 DB 获取文件路径
        KnowledgeDocumentEntity doc = docService.getByDocId(testDocId);
        assertNotNull(doc, "文档记录应存在");

        // 直接调用 ingestionService（绕过 MQ，同步执行）
        ingestionService.ingest(TEST_KB_CODE, testDocId, doc.getFilePath(), doc.getFileName());

        // 验证状态变为 COMPLETED
        KnowledgeDocumentEntity updated = docService.getByDocId(testDocId);
        assertEquals(DocumentStatus.COMPLETED, updated.getStatus(),
            "摄入完成后状态应为 COMPLETED");
        assertTrue(updated.getChunkCount() > 0,
            "chunk_count 应大于 0，实际: " + updated.getChunkCount());
        ingestionDone = true;
        log.info("文档摄入完成: docId={}, chunks={}", testDocId, updated.getChunkCount());
    }

    // ══════════════════════════════════════════════════════════
    //  4. 混合检索
    // ══════════════════════════════════════════════════════════

    @Test
    @Order(30)
    @DisplayName("4.1 混合检索 - 语义匹配")
    void hybridSearch_semantic() {
        Assumptions.assumeTrue(ingestionDone, "跳过：摄入测试未成功");
        List<SearchResult> results = hybridSearchService.search(
            "Spring AI 支持哪些大语言模型？", TEST_KB_CODE, 3);

        assertFalse(results.isEmpty(), "检索结果不应为空");
        log.info("语义检索结果数: {}", results.size());
        for (SearchResult r : results) {
            log.info("  score={}, content={}", r.score(),
                r.content().substring(0, Math.min(80, r.content().length())));
            assertNotNull(r.metadata().get("kb_code"));
            assertEquals(TEST_KB_CODE, r.metadata().get("kb_code"));
        }
    }

    @Test
    @Order(31)
    @DisplayName("4.2 混合检索 - 关键词匹配")
    void hybridSearch_keyword() {
        Assumptions.assumeTrue(ingestionDone, "跳过：摄入测试未成功");
        List<SearchResult> results = hybridSearchService.search(
            "TokenTextSplitter", TEST_KB_CODE, 3);

        assertFalse(results.isEmpty(), "关键词检索结果不应为空");
        boolean containsKeyword = results.stream()
            .anyMatch(r -> r.content().contains("TokenTextSplitter"));
        assertTrue(containsKeyword, "结果中应包含关键词 TokenTextSplitter");
        log.info("关键词检索结果数: {}", results.size());
    }

    @Test
    @Order(32)
    @DisplayName("4.3 混合检索 - 不存在的知识库返回空")
    void hybridSearch_nonexistentKb() {
        List<SearchResult> results = hybridSearchService.search(
            "测试查询", "nonexistent_kb_xyz", 3);
        assertTrue(results.isEmpty(), "不存在的知识库应返回空结果");
    }

    // ══════════════════════════════════════════════════════════
    //  5. 清理：删除文档 & 知识库
    // ══════════════════════════════════════════════════════════

    @Test
    @Order(90)
    @DisplayName("5.1 删除测试文档")
    void deleteDocument() {
        if (testDocId != null) {
            docService.delete(testDocId);
            assertNull(docService.getByDocId(testDocId), "文档删除后应查不到");
            log.info("文档已删除: docId={}", testDocId);
        }
    }

    @Test
    @Order(91)
    @DisplayName("5.2 删除测试知识库")
    void deleteKnowledgeBase() {
        kbService.delete(TEST_KB_CODE);
        assertNull(kbService.getByCode(TEST_KB_CODE), "知识库删除后应查不到");
        log.info("知识库已删除: kbCode={}", TEST_KB_CODE);
    }

    @Test
    @Order(92)
    @DisplayName("5.3 删除不存在的知识库 → 抛异常")
    void deleteNonexistentKb_throwsException() {
        RagException ex = assertThrows(RagException.class,
            () -> kbService.delete("nonexistent_kb_xyz"));
        assertEquals(RagErrorCode.KB_NOT_FOUND, ex.getErrorCode());
    }
}
