package com.ye.decision.rag.controller;

import com.ye.decision.common.Result;
import com.ye.decision.rag.dto.KnowledgeBaseReq;
import com.ye.decision.rag.dto.KnowledgeBaseVO;
import com.ye.decision.rag.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    public KnowledgeBaseController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    @PostMapping
    public Result<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseReq req) {
        return Result.ok(kbService.create(req));
    }

    @GetMapping
    public Result<List<KnowledgeBaseVO>> listAll() {
        return Result.ok(kbService.listAll());
    }

    @GetMapping("/{kbCode}")
    public Result<KnowledgeBaseVO> getByCode(@PathVariable String kbCode) {
        return Result.ok(KnowledgeBaseVO.from(kbService.requireByCode(kbCode)));
    }

    @PutMapping("/{kbCode}")
    public Result<KnowledgeBaseVO> update(@PathVariable String kbCode, @Valid @RequestBody KnowledgeBaseReq req) {
        return Result.ok(kbService.update(kbCode, req));
    }

    @DeleteMapping("/{kbCode}")
    public Result<Void> delete(@PathVariable String kbCode) {
        kbService.delete(kbCode);
        return Result.ok(null);
    }
}
