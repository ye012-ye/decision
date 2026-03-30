package com.ye.decision.dto;

/**
 * ReAct 推理链路中的单个事件，用于 SSE 推送给前端。
 *
 * @param type    事件类型：thought / action / observation / answer
 * @param content 事件内容
 */
public record ReActEvent(String type, String content) {

    public static ReActEvent thought(String content) {
        return new ReActEvent("thought", content);
    }

    public static ReActEvent action(String toolName, String arguments) {
        return new ReActEvent("action", toolName + " | " + arguments);
    }

    public static ReActEvent observation(String content) {
        return new ReActEvent("observation", content);
    }

    public static ReActEvent answer(String content) {
        return new ReActEvent("answer", content);
    }
}
