# -*- coding: utf-8 -*-
"""Replace the 6 bullets with 11 bullets covering more tech depth."""
import sys
sys.stdout.reconfigure(encoding='utf-8')

import copy
import docx
from docx.oxml.ns import qn

RESUME = r'C:\Users\35502\Desktop\简历.docx'

NEW_BULLETS = [
    '基于 Thought → Action → Observation → Answer 的 ReAct 范式实现 Agent 推理引擎，设置 MAX_REACT_STEPS 防止死循环，每一步状态以事件流形式分类推送前端。',
    '基于 Spring WebFlux 的 Flux + FluxSink 将 ReActEvent（thought/action/observation/answer）以 SSE 方式流式推送前端，首 token 延迟大幅降低，显著改善对话体感。',
    '设计关键词意图识别映射表，对用户消息做本地预匹配后按需将相关工具注入本轮 Prompt，显著减少 LLM 工具选择空间与 Token 消耗并降低误选概率。',
    '基于 Milvus 向量库实现 RAG 混合检索：稠密向量（text-embedding-v3, 1024 维）与 BM25 稀疏向量双路召回，通过 RRF 融合排序，显著提升长尾查询命中率。',
    '基于 Apache Tika + TokenTextSplitter 构建多格式文档摄入管线，区分瞬时与永久故障，结合 RabbitMQ 自动重试与 DocumentStatus 状态机管理保障最终一致性。',
    '基于 kb_code 元数据过滤实现多租户知识库隔离，单一 Milvus 集合服务多业务域查询，并通过 doc_id/chunk_index 元数据支持检索结果来源溯源。',
    '基于 Redisson 自研 ChatMemoryRepository：Redis 承载热数据保障低延迟响应、RabbitMQ 异步归档 MySQL、pending 集合 + 定时任务兜底，并结合 MessageWindowChatMemory 动态控制历史窗口平衡 Token 成本。',
    '基于 CompletableFuture + 自定义线程池实现 Tool Calling 并行执行，多工具 fan-out 并发触发、单调用降级为同步路径避免线程池开销，显著降低多工具链路端到端延迟。',
    '自研 McpToolRegistry 解决 MCP Server 动态接入问题：启动期非阻塞、@Scheduled 定时探活、ping liveness 检测 + SSE 自愈重建，实现工具运行时热挂载与故障自动恢复。',
    '基于策略模式封装工单类型与处理人映射规则，通过 Tool Calling 暴露"创建-查询-更新-关闭"四类操作让 LLM 驱动工单流转，并联动 RabbitMQ 触发邮件通知，打通 AI 对话与业务落库闭环。',
    '通过 Nacos 配置中心托管大模型 API Key、Milvus 连接、RAG 相似度阈值等敏感与可调参数，支持不停机热更新与 dev/prod 多环境配置隔离。',
]


def set_runs_text(p_element, texts):
    runs = p_element.findall(qn('w:r'))
    idx = 0
    for r in runs:
        for t in r.findall(qn('w:t')):
            if idx < len(texts):
                t.text = texts[idx]
                idx += 1


def main():
    doc = docx.Document(RESUME)

    # 1. 定位：慧云智学 bullet 模板 [18] + 新项目 "个人职责" 段 + 新项目现有 bullets 区间
    huiyun_bullet_template = None
    duties_new_p = None
    existing_new_bullets = []
    seen_new_title = False
    honor_anchor = None

    for p in doc.paragraphs:
        txt = p.text.strip()
        if huiyun_bullet_template is None and p.style.name == 'List Paragraph' and txt.startswith('通过Redis+RabbitMQ'):
            huiyun_bullet_template = p._p
        if txt.startswith('智策 ·'):
            seen_new_title = True
            continue
        if seen_new_title:
            if txt.startswith('个人职责：'):
                duties_new_p = p._p
                continue
            if p.style.name == 'List Paragraph' and txt and txt != '蓝桥杯省级一等奖' and txt != '大学英语四、六级证书':
                existing_new_bullets.append(p._p)
            if txt == '荣誉证书':
                honor_anchor = p._p
                break

    assert huiyun_bullet_template is not None, '未找到慧云智学 bullet 模板'
    assert duties_new_p is not None, '未找到新项目 个人职责 段落'
    assert honor_anchor is not None, '未找到 荣誉证书 锚点'
    print(f'Template OK; existing new bullets: {len(existing_new_bullets)}')

    # 2. 删掉现有的新 bullet
    for el in existing_new_bullets:
        el.getparent().remove(el)

    # 3. deepcopy 模板构造 11 条新 bullet，插入到 荣誉证书 之前
    for text in NEW_BULLETS:
        new_p = copy.deepcopy(huiyun_bullet_template)
        set_runs_text(new_p, [text])
        honor_anchor.addprevious(new_p)

    doc.save(RESUME)
    print(f'OK — replaced with {len(NEW_BULLETS)} bullets')


if __name__ == '__main__':
    main()
