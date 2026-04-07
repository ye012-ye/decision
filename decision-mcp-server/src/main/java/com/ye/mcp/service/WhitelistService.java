package com.ye.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ye.mcp.config.McpProperties;
import com.ye.mcp.domain.dto.TableWhitelistReq;
import com.ye.mcp.domain.entity.WhitelistEntity;
import com.ye.mcp.exception.McpErrorCode;
import com.ye.mcp.exception.McpException;
import com.ye.mcp.mapper.WhitelistMapper;
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
 * 合并静态配置（{@code mcp.table-whitelist}）与 DB 动态白名单，
 * 维护内存缓存以减少数据库访问。
 *
 * @author ye
 */
@Service
public class WhitelistService {

    private static final Logger log = LoggerFactory.getLogger(WhitelistService.class);

    private final WhitelistMapper whitelistMapper;
    private final McpProperties mcpProperties;

    /**
     * 内存缓存（仅 DB 动态白名单部分）。
     * volatile 保证多线程可见性；ReentrantReadWriteLock 保证读写安全。
     * 写少读多场景下，读锁不互斥，写锁排他，性能优于 synchronized。
     */
    private volatile Set<String> cachedDbWhitelist;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public WhitelistService(WhitelistMapper whitelistMapper, McpProperties mcpProperties) {
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
        // 白名单为空 → 开放模式：只要不在黑名单就放行
        // 白名单非空 → 严格模式：表必须在白名单内才允许
        return whitelist.isEmpty() || whitelist.contains(lower);
    }

    /**
     * 添加表到白名单。幂等操作：如果表已存在则重新启用并更新描述。
     */
    public void addTable(TableWhitelistReq req) {
        String lower = req.tableName().toLowerCase(Locale.ROOT);
        Long count = whitelistMapper.selectCount(
            new LambdaQueryWrapper<WhitelistEntity>()
                .eq(WhitelistEntity::getTableName, lower)
        );
        if (count > 0) {
            // 已存在则启用
            WhitelistEntity entity = whitelistMapper.selectOne(
                new LambdaQueryWrapper<WhitelistEntity>()
                    .eq(WhitelistEntity::getTableName, lower)
            );
            entity.setStatus(1);
            entity.setDescription(req.description());
            whitelistMapper.updateById(entity);
        } else {
            WhitelistEntity entity = new WhitelistEntity();
            entity.setTableName(lower);
            entity.setDescription(req.description());
            entity.setStatus(1);
            whitelistMapper.insert(entity);
        }
        refreshCache();
        log.info("Table '{}' added to MCP whitelist", lower);
    }

    /**
     * 从白名单移除表。软删除：将 status 置为 0，不物理删除记录。
     */
    public void removeTable(String tableName) {
        String lower = tableName.toLowerCase(Locale.ROOT);
        WhitelistEntity entity = whitelistMapper.selectOne(
            new LambdaQueryWrapper<WhitelistEntity>()
                .eq(WhitelistEntity::getTableName, lower)
        );
        if (entity == null) {
            throw new McpException(McpErrorCode.WHITELIST_NOT_FOUND, tableName);
        }
        entity.setStatus(0);
        whitelistMapper.updateById(entity);
        refreshCache();
        log.info("Table '{}' removed from MCP whitelist", lower);
    }

    public List<WhitelistEntity> listAll() {
        return whitelistMapper.selectList(null);
    }

    /**
     * 从 DB 重新加载活跃白名单到内存缓存。
     * 加写锁保证刷新期间读操作不会拿到半更新状态。
     * 如果 DB 查询失败，保留上一次缓存值（降级策略），不让异常扩散。
     */
    private void refreshCache() {
        lock.writeLock().lock();
        try {
            List<WhitelistEntity> active = whitelistMapper.selectList(
                new LambdaQueryWrapper<WhitelistEntity>()
                    .eq(WhitelistEntity::getStatus, 1)
            );
            cachedDbWhitelist = active.stream()
                .map(e -> e.getTableName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
            log.debug("MCP whitelist cache: {}", cachedDbWhitelist);
        } catch (Exception e) {
            log.warn("Failed to refresh MCP whitelist cache, using previous", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
