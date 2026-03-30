package com.ye.decision.rag.controller;

import com.ye.decision.common.Result;
import com.ye.decision.rag.dto.DocumentUploadResp;
import com.ye.decision.rag.dto.KnowledgeDocumentVO;
import com.ye.decision.rag.service.KnowledgeDocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/kb/{kbCode}/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService docService;

    public KnowledgeDocumentController(KnowledgeDocumentService docService) {
        this.docService = docService;
    }

    @PostMapping
    public Result<DocumentUploadResp> upload(@PathVariable String kbCode,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "uploadedBy", required = false) String uploadedBy) throws IOException {
        return Result.ok(docService.upload(kbCode, file, uploadedBy));
    }

    @GetMapping
    public Result<List<KnowledgeDocumentVO>> list(@PathVariable String kbCode) {
        return Result.ok(docService.listByKbCode(kbCode));
    }

    @GetMapping("/{docId}/status")
    public Result<KnowledgeDocumentVO> getStatus(@PathVariable String kbCode,
                                                  @PathVariable String docId) {
        return Result.ok(KnowledgeDocumentVO.from(docService.getByDocId(docId)));
    }

    @DeleteMapping("/{docId}")
    public Result<Void> delete(@PathVariable String kbCode, @PathVariable String docId) {
        docService.delete(docId);
        return Result.ok(null);
    }
}
