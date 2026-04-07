package com.ye.decision.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ye.decision.rag.domain.dto.KnowledgeBaseReq;
import com.ye.decision.rag.domain.dto.KnowledgeBaseVO;
import com.ye.decision.rag.domain.entity.KnowledgeBaseEntity;
import com.ye.decision.rag.domain.entity.KnowledgeDocumentEntity;
import com.ye.decision.rag.domain.enums.KbStatus;
import com.ye.decision.rag.exception.RagErrorCode;
import com.ye.decision.rag.exception.RagException;
import com.ye.decision.rag.mapper.KnowledgeBaseMapper;
import com.ye.decision.rag.mapper.KnowledgeDocumentMapper;
import com.ye.decision.rag.search.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库管理服务。
 *
 * @author ye
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final DocumentStore documentStore;

    public KnowledgeBaseService(KnowledgeBaseMapper kbMapper,
                                KnowledgeDocumentMapper docMapper,
                                DocumentStore documentStore) {
        this.kbMapper = kbMapper;
        this.docMapper = docMapper;
        this.documentStore = documentStore;
    }

    public KnowledgeBaseVO create(KnowledgeBaseReq req) {
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

    @Transactional(rollbackFor = Exception.class)
    public void delete(String kbCode) {
        requireByCode(kbCode);

        documentStore.delete("kb_code == '" + kbCode + "'");

        docMapper.delete(
            new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbCode, kbCode));

        kbMapper.delete(
            new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getKbCode, kbCode));

        log.info("Knowledge base deleted: kbCode={}", kbCode);
    }
}
