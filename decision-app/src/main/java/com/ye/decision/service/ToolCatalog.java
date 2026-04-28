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
        Map<String, ToolCallback> index = new LinkedHashMap<>();
        for (ToolCallback cb : getToolCallbacks()) {
            index.put(cb.getToolDefinition().name(), cb);
        }
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
}
