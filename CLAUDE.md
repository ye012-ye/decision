# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

Multi-module Maven project plus a standalone Vue frontend:

- `decision-app/` — Spring Boot 3.3 AI agent service (port 8080). Contains the ReAct loop, RAG, tools, chat SSE, Feign clients, MyBatis-Plus, Redisson-backed chat memory. Group `com.ye.decision`.
- `decision-mcp-server/` — Independent Spring AI MCP server (port 8081) exposing database/whitelist tools over SSE. `decision-app` connects to it as an MCP client. Group `com.ye.mcp`.
- `decision-web/` — Vue 3 + Vite + TypeScript frontend consuming the chat SSE API.
- `docker/docker-compose.yml` — Local infra (MySQL, etc.). `docs/sql` holds schema reference.
- Root `pom.xml` is a BOM-only aggregator. There is no top-level `src/` — the directory exists but is empty.

## Build & Run

Backend (from repo root, uses wrapper `mvnw` / `mvnw.cmd`):

```bash
./mvnw -pl decision-app -am spring-boot:run          # run agent service (8080)
./mvnw -pl decision-mcp-server -am spring-boot:run    # run MCP server (8081) — start this BEFORE decision-app
./mvnw clean package -DskipTests                     # build all modules
./mvnw -pl decision-app test                         # run one module's tests
./mvnw -pl decision-app -Dtest=AgentServiceTest test # run one test class
```

Frontend (`decision-web/`):

```bash
npm run dev            # Vite dev server
npm run build          # vue-tsc type-check + vite build
npm run test           # vitest (unit)
npm run test:e2e       # Playwright e2e (auto-installs chromium)
```

## External Dependencies (required at runtime)

Defaults in `decision-app/src/main/resources/bootstrap.yaml` point at `192.168.83.128`:

- **Nacos** `:8848` (namespace `dev`) — config + service discovery. App will fail to start without it.
- **MySQL** `:3306/decision` (root/1234) — MyBatis-Plus + Flyway-style schema in `decision-app/src/main/resources/db/V2__knowledge_tables.sql`.
- **Redis** `:6379` — Redisson starter; backs `RedissonChatMemoryRepository` (chat memory) and `QueryRedisTool`.
- **RabbitMQ** `:5672` — async document ingestion for RAG.
- **Milvus** `:19530`, collection `knowledge_vectors`, 1024-dim dense vectors — vector store for RAG.
- **DashScope** (Aliyun) — LLM + embeddings via `spring-ai-alibaba-starter-dashscope`. API key from env `DASHSCOPE_API_KEY` or Nacos.
- **decision-mcp-server** `:8081` — MCP tools exposed over SSE at `http://localhost:8081` (see `spring.ai.mcp.client.sse.connections.decision-mcp`).

Sensitive config is expected to be overridden by Nacos; the bootstrap.yaml values are placeholders/defaults.

## Architecture

### Agent topology (`decision-app`)

The core is `agent.AlibabaAgent` — a Spring AI Alibaba Agent Framework composition:
**`LlmRoutingAgent` (root) → 5 domain `ReactAgent`s** (`knowledge` / `data` / `workorder` / `external` / `chat`). All sub-agents share one `ChatMemory` keyed by `sessionId`.

Wiring is in `agent.config.AgentConfig`. The router is built by `agent.router.RouterAgentFactory` — its system prompt is auto-assembled from each domain's `name() + description()`, so adding a new domain requires zero changes to the factory: just add an `AbstractDomainAgent` `@Bean` under `agent.domains.<x>/`.

SSE event names the frontend listens on (produced by `agent.stream.GraphEventAdapter` translating `NodeOutput`):
- `route` — router decision (lower-cased domain name)
- `thought` — model reasoning text
- `action` — tool invocation (`toolName | arguments`)
- `observation` — tool result
- `answer` — final answer chunk
- `done` / `error`

`ChatMemory` is the custom `RedissonChatMemoryRepository` with window size `decision.agent.memory-window-size` (default 20). The router fallback domain is configurable via `decision.agent.router.fallback-agent` (default `chat`).

### Per-domain tool selection (replaces legacy keyword filtering)

Each domain `@Bean` in `AgentConfig` declares its tool set explicitly via `ToolCatalog.byNames(...)`. There is **no runtime keyword filtering** anymore — the LLM router picks the domain, and the chosen domain's `ReactAgent` only sees its own tools. Adding a new tool means: (1) register it in `AiConfig.toolCatalog(...)`, then (2) list its name in the relevant domain's `byNames(...)` call.

Tool sources are unified in `service.ToolCatalog`:
- Local `@Tool`/function-style beans under `com.ye.decision.tool` (`CallExternalApiTool`, `KnowledgeSearchTool`, `QueryMysqlTool`, `QueryRedisTool`, `WorkOrderTool`)
- Remote MCP tools discovered via `service.McpToolRegistry` from `decision-mcp-server` (no name prefix — names match the MCP method names: `listTables` / `describeTable` / `queryData` / `executeSql`)

Because MCP tools are loaded asynchronously, `AgentConfig.dataAgent(...)` calls `mcpToolRegistry.refreshNow()` synchronously before `byNames(...)` to ensure MCP tools are present at bean wiring time. The MCP server **must** be running before `decision-app` starts, or wiring will throw `IllegalStateException` (intentional fail-fast).

### RAG subsystem (`com.ye.decision.rag.*`)

Self-contained package with its own controller/service/mapper/mq/search layers. Ingestion flow: upload → RabbitMQ → `DocumentIngestionService` → chunking (`chunk-size: 512`, `chunk-overlap: 100`) → DashScope embeddings → Milvus `knowledge_vectors`. Retrieval uses RRF fusion (`rrf-k: 60`) and a `similarity-threshold` of 0.7. `KnowledgeSearchTool` is the agent-facing entry point.

### MCP server (`decision-mcp-server`)

Minimal Spring AI MCP server. Exposes `tool.DatabaseTools` (and the audit/whitelist REST controllers which are **not** MCP tools — they're admin APIs). Runs standalone on 8081; `decision-app` connects as a client. Treat it as a separate deployable: changes to its tool signatures require restarting both processes.

### Frontend chat stream

`decision-web` consumes the SSE stream via `EventSource`, dispatching each named event into a Pinia store that the chat UI renders as a disclosure ("thought" → "action" → "observation" → "answer"). If you change backend event names, `decision-web/src/stores` and `decision-web/src/api` both need updating.

## Conventions worth knowing

- Chinese comments are common in service/tool classes — preserve them when editing; they often document non-obvious business rules.
- `decision.*` config keys in `bootstrap.yaml` are the project's own namespace (agent, rag, external, notification). Prefer extending that tree over inventing new roots.
- MyBatis-Plus is configured with `map-underscore-to-camel-case: true` and `id-type: auto` globally — don't override per-mapper unless necessary.
- `spring.sql.init.mode: never` — do not rely on Spring's schema init; use the SQL files in `decision-app/src/main/resources/db/` and `docs/sql/`.
