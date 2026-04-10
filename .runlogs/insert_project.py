# -*- coding: utf-8 -*-
"""Insert the decision project as a new 项目经历 entry before 荣誉证书."""
import sys
sys.stdout.reconfigure(encoding='utf-8')

import docx
from copy import deepcopy

RESUME = r'C:\Users\35502\Desktop\简历.docx'

# (text, style_name)
NEW_PARAGRAPHS = [
    ('企业智能决策助手\t2026-02 至 2026-04', 'Normal'),
    ('技术栈：SpringBoot + SpringCloudAlibaba(Nacos) + Spring AI Alibaba + Milvus + Redis(Redisson) + MySQL + MyBatis-Plus + RabbitMQ + MCP协议 + 通义千问-Max 等', 'Normal'),
    ('项目介绍：面向企业内部场景的 AI 决策助手平台，集成通义千问-Max/Deepseek-V3 大模型，提供基于 RAG 的企业知识问答、多数据源 Tool Calling（MySQL/Redis/外部API）、客服工单自动化以及 MCP 协议动态工具扩展能力。前后端分离，支持流式对话、多轮上下文记忆与会话隔离。', 'Normal'),
    ('个人职责：负责后端核心链路，包括 RAG 检索管线、ChatMemory 持久化、Tool Calling 工具体系、MCP 动态客户端治理及文档摄入管线的设计与实现。', 'Normal'),
    ('基于 Milvus 落地 RAG 混合检索方案：稠密向量（text-embedding-v3, 1024 维）结合 BM25 稀疏向量双路召回，通过 RRF（Reciprocal Rank Fusion）算法融合排序，相比单一语义检索显著提升长尾查询命中率与召回精度。', 'List Paragraph'),
    ('基于 Apache Tika + TokenTextSplitter 构建文档摄入管线，按 token 粒度切片并注入 kb_code/doc_id/chunk_index 元数据实现多知识库隔离与来源溯源；针对 Milvus 写入区分"永久性故障"（解析失败直接标记 FAILED）与"瞬时故障"（网络超时抛出交由 RabbitMQ 自动重试），保障最终一致性。', 'List Paragraph'),
    ('基于 Redisson 自研 ChatMemoryRepository 实现对话上下文持久化：Redis 承载热数据保障低延迟响应，saveAll 时同步发布 MQ 消息异步写入 MySQL 归档，并通过 Redis pending 集合 + 定时任务兜底，避免消息丢失导致的数据不一致。', 'List Paragraph'),
    ('基于 Spring AI FunctionToolCallback 封装 MySQL 查询、Redis 查询、外部 API 调用、知识库检索、工单管理等 5+ 业务工具，结合 Prompt 工程让大模型根据意图自主编排调用链，覆盖智能客服、数据查询、工单自动处理等场景。', 'List Paragraph'),
    ('自研 McpToolRegistry 解决 MCP Server 动态接入问题：通过 initialized=false 实现启动期非阻塞；@Scheduled 后台定时探活，接入 ping() 作 liveness 检测，失败后 closeGracefully 并在下一轮自动重建 SSE 连接实现故障自愈；ToolCatalog 通过 volatile 缓存 + ObjectProvider 动态合并本地与 MCP 工具，无需重启即可热挂载新工具。', 'List Paragraph'),
    ('基于策略模式封装工单类型与处理人映射规则，通过 Tool Calling 暴露"创建/查询/更新/关闭"四类操作让 LLM 直接驱动工单流转，并联动 RabbitMQ 异步触发邮件通知，实现"AI 对话 → 工单落库 → 处理人触达"一站式闭环。', 'List Paragraph'),
]


def main():
    doc = docx.Document(RESUME)

    # 找 "荣誉证书" 段落，作为锚点 —— 在它之前插入新项目
    anchor = None
    for p in doc.paragraphs:
        if p.text.strip() == '荣誉证书':
            anchor = p
            break
    if anchor is None:
        raise RuntimeError('未找到 "荣誉证书" 锚点段落')

    # 为保持样式一致（字体、段距等），克隆一个已有 List Paragraph 和 Normal 的 pPr 来参考
    # python-docx 的 insert_paragraph_before 会用指定 style name，足够
    for text, style in NEW_PARAGRAPHS:
        new_p = anchor.insert_paragraph_before(text, style=style)
        # run 级别的字号/字体从锚点所在的已有样式继承即可

    doc.save(RESUME)
    print('OK — inserted', len(NEW_PARAGRAPHS), 'paragraphs')


if __name__ == '__main__':
    main()
