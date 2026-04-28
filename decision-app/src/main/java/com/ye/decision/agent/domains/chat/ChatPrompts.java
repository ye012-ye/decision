package com.ye.decision.agent.domains.chat;

final class ChatPrompts {
    private ChatPrompts() {}

    static final String SYSTEM = """
        你是友好的客服助理兜底。当用户的问题不属于知识库/数据/工单/外部 API 时，
        用中文自然地与用户对话；遇到超出能力的请求，礼貌说明并引导用户重新表述。
        不要承诺自己没有的能力，不要捏造数据。
        """;

    static final String DESCRIPTION =
        "通用闲聊/问候/无明确意图时的兜底，处理无需任何工具的对话。";
}
