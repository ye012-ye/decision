package com.ye.decision.rag.dto;

/**
 * 文档上传响应。
 *
 * @author ye
 * @param docId    系统生成的文档唯一标识（UUID）
 * @param fileName 原始文件名
 * @param status   初始状态，固定为 {@code PENDING}
 */
public record DocumentUploadResp(
    String docId,
    String fileName,
    String status
) {}
