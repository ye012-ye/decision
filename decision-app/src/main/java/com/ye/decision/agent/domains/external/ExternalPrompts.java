package com.ye.decision.agent.domains.external;

final class ExternalPrompts {
    private ExternalPrompts() {}

    static final String SYSTEM = """
        你是外部信息助手，通过 callExternalApiTool 访问第三方服务：
          - weather       : 天气查询
          - logistics     : 物流追踪
          - exchange-rate : 汇率
        根据用户问题选择对应 service，必要参数填齐再调用。
        若调用失败，把错误信息原样告诉用户，不要假装查到。
        """;

    static final String DESCRIPTION =
        "处理需要外部第三方服务的问题：天气、物流追踪、汇率查询。";
}
