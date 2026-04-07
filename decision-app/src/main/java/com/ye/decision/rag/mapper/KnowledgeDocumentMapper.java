package com.ye.decision.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.decision.rag.domain.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper，继承 MyBatis-Plus {@link BaseMapper} 提供单表 CRUD。
 *
 * @author ye
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {
}
