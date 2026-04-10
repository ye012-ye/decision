package com.ye.decision.service;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 提供当前可用工具快照。
 */
@FunctionalInterface
public interface ToolCatalog {

    List<ToolCallback> getToolCallbacks();
}
