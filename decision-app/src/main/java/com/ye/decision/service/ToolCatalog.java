package com.ye.decision.service;

import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供当前可用工具快照。
 */
public interface ToolCatalog {

    /** 返回当前所有工具（含本地 + MCP 远端动态发现）。 */
    List<ToolCallback> getToolCallbacks();

    /**
     * 按名字精确筛选工具。任一名字找不到 → 抛 {@link IllegalStateException}
     * （让启动 / Bean 装配阶段失败，而不是运行时悄悄丢工具）。
     */
    default List<ToolCallback> byNames(String... names) {
        if (names == null || names.length == 0) {
            return List.of();
        }
        Map<String, ToolCallback> index = index(getToolCallbacks());
        List<ToolCallback> selected = new ArrayList<>(names.length);
        for (String name : names) {
            ToolCallback cb = index.get(name);
            if (cb == null) {
                throw new IllegalStateException("Tool not found in catalog: " + name
                    + " (available: " + index.keySet() + ")");
            }
            selected.add(cb);
        }
        return List.copyOf(selected);
    }

    /**
     * 按名字筛选工具，但忽略当前不在 catalog 中的名字（不抛异常）。
     * 适用于 MCP 等"可能尚未就绪"的远端工具：缺失即跳过，保证 Bean 装配不被阻塞。
     *
     * @return 命中的工具（保持入参顺序）；全部缺失时返回空列表
     */
    default List<ToolCallback> byNamesIfPresent(String... names) {
        if (names == null || names.length == 0) {
            return List.of();
        }
        Map<String, ToolCallback> index = index(getToolCallbacks());
        List<ToolCallback> selected = new ArrayList<>(names.length);
        for (String name : names) {
            ToolCallback cb = index.get(name);
            if (cb != null) {
                selected.add(cb);
            }
        }
        return List.copyOf(selected);
    }

    private static Map<String, ToolCallback> index(List<ToolCallback> callbacks) {
        Map<String, ToolCallback> index = new LinkedHashMap<>();
        for (ToolCallback cb : callbacks) {
            index.put(cb.getToolDefinition().name(), cb);
        }
        return index;
    }
}
