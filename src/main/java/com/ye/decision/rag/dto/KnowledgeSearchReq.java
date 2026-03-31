package com.ye.decision.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * RAG 知识库检索工具的入参，由 Agent 在 ReAct 循环中自动填充。
 *
 * @author ye
 * @param query 自然语言检索内容
 * @param kbCode 目标知识库编码，用于 Milvus 过滤表达式
 * @param topK   返回最相似的文档片段数量，默认 5
 */
public record KnowledgeSearchReq(

    @NotBlank(message = "查询内容不能为空")
    @JsonProperty(required = true)
    String query,

    @NotBlank(message = "知识库编码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "知识库编码只允许字母、数字、下划线、连字符")
    @JsonProperty(required = true)
    String kbCode,

    @JsonProperty(defaultValue = "5")
    int topK
) {}
