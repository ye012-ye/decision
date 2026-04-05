package com.ye.decision.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ye.decision.mcp.config.McpProperties;
import com.ye.decision.mcp.domain.dto.TableWhitelistReq;
import com.ye.decision.mcp.domain.entity.McpWhitelistEntity;
import com.ye.decision.mcp.exception.McpErrorCode;
import com.ye.decision.mcp.exception.McpException;
import com.ye.decision.mcp.mapper.McpWhitelistMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * MCP 表访问白名单管理。
 * <p>
 * 合并静态配置（{@code decision.mcp.table-whitelist}）与 DB 动态白名单，
 * 维护内存缓存以减少数据库访问。
 *
 * @author ye
 */
@Service
public class McpWhitelistService {

    private static final Logger log = LoggerFactory.getLogger(McpWhitelistService.class);

    private final McpWhitelistMapper whitelistMapper;
    private final McpProperties mcpProperties;

    /** 内存缓存（DB 白名单部分），写少读多场景 */
    private volatile Set<String> cachedDbWhitelist;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public McpWhitelistService(McpWhitelistMapper whitelistMapper, McpProperties mcpProperties) {
        this.whitelistMapper = whitelistMapper;
        this.mcpProperties = mcpProperties;
        refreshCache();
    }

    /**
     * 获取生效的白名单（静态配置 + DB 活跃记录合并）。
     * 所有表名统一小写比较。
     */
    public Set<String> getEffectiveWhitelist() {
        Set<String> merged = new HashSet<>();
        // 静态配置
        mcpProperties.getTableWhitelist().stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .forEach(merged::add);
        // DB 缓存
        lock.readLock().lock();
        try {
            if (cachedDbWhitelist != null) {
                merged.addAll(cachedDbWhitelist);
            }
        } finally {
            lock.readLock().unlock();
        }
        return merged;
    }

    /**
     * 获取黑名单。
     */
    public Set<String> getBlacklist() {
        return mcpProperties.getTableBlacklist().stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }

    /**
     * 判断表是否允许访问：不在黑名单，且白名单非空时必须在白名单内。
     */
    public boolean isAllowed(String tableName) {
        String lower = tableName.toLowerCase(Locale.ROOT);
        if (getBlacklist().contains(lower)) {
            return false;
        }
        Set<String> whitelist = getEffectiveWhitelist();
        // 白名单为空时，允许所有非黑名单表
        return whitelist.isEmpty() || whitelist.contains(lower);
    }

    public void addTable(TableWhitelistReq req) {
        String lower = req.tableName().toLowerCase(Locale.ROOT);
        // 检查是否已存在
        Long count = whitelistMapper.selectCount(
            new LambdaQueryWrapper<McpWhitelistEntity>()
                .eq(McpWhitelistEntity::getTableName, lower)
        );
        if (count > 0) {
            // 已存在则启用
            McpWhitelistEntity entity = whitelistMapper.selectOne(
                new LambdaQueryWrapper<McpWhitelistEntity>()
                    .eq(McpWhitelistEntity::getTableName, lower)
            );
            entity.setStatus(1);
            entity.setDescription(req.description());
            whitelistMapper.updateById(entity);
        } else {
            McpWhitelistEntity entity = new McpWhitelistEntity();
            entity.setTableName(lower);
            entity.setDescription(req.description());
            entity.setStatus(1);
            whitelistMapper.insert(entity);
        }
        refreshCache();
        log.info("Table '{}' added to MCP whitelist", lower);
    }

    public void removeTable(String tableName) {
        String lower = tableName.toLowerCase(Locale.ROOT);
        McpWhitelistEntity entity = whitelistMapper.selectOne(
            new LambdaQueryWrapper<McpWhitelistEntity>()
                .eq(McpWhitelistEntity::getTableName, lower)
        );
        if (entity == null) {
            throw new McpException(McpErrorCode.WHITELIST_NOT_FOUND, tableName);
        }
        entity.setStatus(0);
        whitelistMapper.updateById(entity);
        refreshCache();
        log.info("Table '{}' removed from MCP whitelist", lower);
    }

    public List<McpWhitelistEntity> listAll() {
        return whitelistMapper.selectList(null);
    }

    private void refreshCache() {
        lock.writeLock().lock();
        try {
            List<McpWhitelistEntity> active = whitelistMapper.selectList(
                new LambdaQueryWrapper<McpWhitelistEntity>()
                    .eq(McpWhitelistEntity::getStatus, 1)
            );
            cachedDbWhitelist = active.stream()
                .map(e -> e.getTableName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Failed to refresh MCP whitelist cache, using previous", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
