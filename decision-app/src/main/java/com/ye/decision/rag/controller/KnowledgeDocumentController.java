package com.ye.decision.rag.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ye.decision.common.Result;
import com.ye.decision.rag.domain.dto.DocumentUploadResp;
import com.ye.decision.rag.domain.dto.KnowledgeDocumentVO;
import com.ye.decision.rag.service.KnowledgeDocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 知识库文档管理接口。
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

    /** 分页列出指定知识库下的文档，按上传时间倒序。 */
    @GetMapping
    public Result<Page<KnowledgeDocumentVO>> list(@PathVariable String kbCode,
                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(docService.listByKbCode(kbCode, pageNum, pageSize));
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
