package com.ye.decision.domain.dto;

/**
 * ReAct 推理链路中的单个事件，用于 SSE 推送给前端。
 *
 * @author ye
 * @param type    事件类型：thought / action / observation / answer
 * @param content 事件内容
 */
public record ReActEvent(String type, String content) {
   /**
     * 推理阶段，模型生成推理文本（Thought）
     */
    public static ReActEvent thought(String content) {
        return new ReActEvent("thought", content);
    }
    /**
     * 模型调用工具
     */
    public static ReActEvent action(String toolName, String arguments) {
        return new ReActEvent("action", toolName + " | " + arguments);
    }
    /**
     * 工具返回结果
     */
    public static ReActEvent observation(String content) {
        return new ReActEvent("observation", content);
    }
    /**
     * 模型生成最终答案
     */
    public static ReActEvent answer(String content) {
        return new ReActEvent("answer", content);
    }
}
