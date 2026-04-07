package com.ye.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.mcp.domain.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author ye
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {}
