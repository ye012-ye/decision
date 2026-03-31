package com.ye.decision.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ye.decision.rag.domain.KbStatus;
import com.ye.decision.rag.dto.KnowledgeBaseReq;
import com.ye.decision.rag.dto.KnowledgeBaseVO;
import com.ye.decision.rag.entity.KnowledgeBaseEntity;
import com.ye.decision.rag.entity.KnowledgeDocumentEntity;
import com.ye.decision.rag.exception.RagErrorCode;
import com.ye.decision.rag.exception.RagException;
import com.ye.decision.rag.mapper.KnowledgeBaseMapper;
import com.ye.decision.rag.mapper.KnowledgeDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库管理服务。
 * <p>
 * 提供知识库的创建（含唯一性校验）、查询、更新和删除。
 * 删除操作在事务中级联清除 Milvus 向量 → 文档记录 → 知识库记录。
 *
 * @author ye
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final VectorStore vectorStore;

    public KnowledgeBaseService(KnowledgeBaseMapper kbMapper,
                                KnowledgeDocumentMapper docMapper,
                                VectorStore vectorStore) {
        this.kbMapper = kbMapper;
        this.docMapper = docMapper;
        this.vectorStore = vectorStore;
    }

    /**
     * 创建知识库。kbCode 不允许重复，重复时抛出 {@link RagException}。
     */
    public KnowledgeBaseVO create(KnowledgeBaseReq req) {
        // 唯一性校验
        if (getByCode(req.kbCode()) != null) {
            throw new RagException(RagErrorCode.KB_CODE_DUPLICATE, req.kbCode());
        }

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setKbCode(req.kbCode());
        entity.setKbName(req.kbName());
        entity.setDescription(req.description());
        entity.setOwner(req.owner());
        entity.setStatus(KbStatus.ACTIVE);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        kbMapper.insert(entity);

        log.info("Knowledge base created: kbCode={}", req.kbCode());
        return KnowledgeBaseVO.from(entity);
    }

    public KnowledgeBaseEntity getByCode(String kbCode) {
        return kbMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getKbCode, kbCode));
    }

    /**
     * 按 kbCode 查询知识库，不存在时抛出 {@link RagException}。
     * 适用于需要确保知识库存在的场景（如上传文档前校验）。
     */
    public KnowledgeBaseEntity requireByCode(String kbCode) {
        KnowledgeBaseEntity entity = getByCode(kbCode);
        if (entity == null) {
            throw new RagException(RagErrorCode.KB_NOT_FOUND, kbCode);
        }
        return entity;
    }

    public List<KnowledgeBaseVO> listAll() {
        return kbMapper.selectList(null)
            .stream()
            .map(KnowledgeBaseVO::from)
            .toList();
    }

    public KnowledgeBaseVO update(String kbCode, KnowledgeBaseReq req) {
        KnowledgeBaseEntity entity = requireByCode(kbCode);
        entity.setKbName(req.kbName());
        entity.setDescription(req.description());
        entity.setOwner(req.owner());
        entity.setUpdatedAt(LocalDateTime.now());
        kbMapper.updateById(entity);

        log.info("Knowledge base updated: kbCode={}", kbCode);
        return KnowledgeBaseVO.from(entity);
    }

    /**
     * 删除知识库及其全部关联数据（事务）。
     * <p>
     * 删除顺序：Milvus 向量 → 文档 DB 记录 → 知识库 DB 记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String kbCode) {
        requireByCode(kbCode);

        // 1. 删除 Milvus 中该知识库的所有向量
        vectorStore.delete("kb_code == '" + kbCode + "'");

        // 2. 删除文档元数据
        docMapper.delete(
            new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbCode, kbCode));

        // 3. 删除知识库记录
        kbMapper.delete(
            new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getKbCode, kbCode));

        log.info("Knowledge base deleted: kbCode={}", kbCode);
    }
}
