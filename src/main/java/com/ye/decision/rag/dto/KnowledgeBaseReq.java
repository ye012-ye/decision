package com.ye.decision.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 知识库创建 / 更新请求体。
 *
 * @author ye
 * @param kbCode      知识库唯一编码，仅允许字母、数字、下划线、连字符
 * @param kbName      知识库名称
 * @param description 可选描述信息
 * @param owner       所有者标识
 */
public record KnowledgeBaseReq(

    @NotBlank(message = "知识库编码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "知识库编码只允许字母、数字、下划线、连字符")
    @Size(max = 64, message = "知识库编码长度不能超过64")
    String kbCode,

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称长度不能超过128")
    String kbName,

    @Size(max = 512, message = "描述长度不能超过512")
    String description,

    @NotBlank(message = "所有者不能为空")
    @Size(max = 64, message = "所有者长度不能超过64")
    String owner
) {}
