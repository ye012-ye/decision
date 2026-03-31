package com.ye.decision.rag.controller;

import com.ye.decision.common.Result;
import com.ye.decision.rag.dto.DocumentUploadResp;
import com.ye.decision.rag.dto.KnowledgeDocumentVO;
import com.ye.decision.rag.service.KnowledgeDocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 知识库文档管理接口。
 * <p>
 * 以 {@code /api/kb/{kbCode}/documents} 为资源路径，提供文档的上传、查询、状态追踪和删除。
 * <p>
 * 上传流程：接收文件 → 落盘 → 写 DB（PENDING） → 发 MQ → 消费端异步完成 解析 → 切片 → 嵌入。
 * 前端可通过 {@code GET /{docId}/status} 轮询摄入进度。
 *
 * @author ye
 */
@RestController
@RequestMapping("/api/kb/{kbCode}/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService docService;

    public KnowledgeDocumentController(KnowledgeDocumentService docService) {
        this.docService = docService;
    }

    /** 上传文档，支持 pdf/doc/docx/txt/md/html/csv/xls/xlsx/pptx。 */
    @PostMapping
    public Result<DocumentUploadResp> upload(@PathVariable String kbCode,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "uploadedBy", required = false) String uploadedBy) throws IOException {
        return Result.ok(docService.upload(kbCode, file, uploadedBy));
    }

    /** 列出指定知识库下的所有文档，按上传时间倒序。 */
    @GetMapping
    public Result<List<KnowledgeDocumentVO>> list(@PathVariable String kbCode) {
        return Result.ok(docService.listByKbCode(kbCode));
    }

    /** 查询文档摄入状态（PENDING → PROCESSING → COMPLETED / FAILED）。 */
    @GetMapping("/{docId}/status")
    public Result<KnowledgeDocumentVO> getStatus(@PathVariable String kbCode,
                                                  @PathVariable String docId) {
        return Result.ok(KnowledgeDocumentVO.from(docService.getByDocId(docId)));
    }

    /** 删除文档，级联清除 Milvus 向量 + 磁盘文件 + DB 记录。 */
    @DeleteMapping("/{docId}")
    public Result<Void> delete(@PathVariable String kbCode, @PathVariable String docId) {
        docService.delete(docId);
        return Result.ok(null);
    }
}
