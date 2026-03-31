package com.ye.decision.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.decision.rag.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper，继承 MyBatis-Plus {@link BaseMapper} 提供单表 CRUD。
 *
 * @author ye
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseEntity> {
}
