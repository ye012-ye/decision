package com.ye.decision.rag.controller;

import com.ye.decision.common.Result;
import com.ye.decision.rag.dto.KnowledgeBaseReq;
import com.ye.decision.rag.dto.KnowledgeBaseVO;
import com.ye.decision.rag.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理接口。
 * <p>
 * 提供知识库的 CRUD 操作，每个知识库通过唯一的 {@code kbCode} 标识，
 * 底层关联 Milvus 向量集合中以 {@code kb_code} 为过滤维度的文档向量。
 *
 * @author ye
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    public KnowledgeBaseController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    /** 创建知识库，kbCode 全局唯一。 */
    @PostMapping
    public Result<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseReq req) {
        return Result.ok(kbService.create(req));
    }

    /** 查询所有知识库。 */
    @GetMapping
    public Result<List<KnowledgeBaseVO>> listAll() {
        return Result.ok(kbService.listAll());
    }

    /** 按编码查询知识库详情。 */
    @GetMapping("/{kbCode}")
    public Result<KnowledgeBaseVO> getByCode(@PathVariable String kbCode) {
        return Result.ok(KnowledgeBaseVO.from(kbService.requireByCode(kbCode)));
    }

    /** 更新知识库基本信息。 */
    @PutMapping("/{kbCode}")
    public Result<KnowledgeBaseVO> update(@PathVariable String kbCode, @Valid @RequestBody KnowledgeBaseReq req) {
        return Result.ok(kbService.update(kbCode, req));
    }

    /** 删除知识库，级联清除 Milvus 向量 + 文档记录。 */
    @DeleteMapping("/{kbCode}")
    public Result<Void> delete(@PathVariable String kbCode) {
        kbService.delete(kbCode);
        return Result.ok(null);
    }
}
