# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the **`decision-web`** module — the Vue 3 frontend for the Decision AI agent platform. For backend context (the agent service, MCP server, SSE event producer), see the repo-root [../CLAUDE.md](../CLAUDE.md).

## Commands

```bash
npm run dev            # Vite dev server on :5173 (proxies /api → :8080)
npm run build          # vue-tsc --noEmit type-check, THEN vite build (type errors fail the build)
npm run test           # vitest run (unit, happy-dom) — excludes tests/e2e
npm run test:watch     # vitest watch mode
npm run test:e2e       # Playwright e2e; auto-runs `playwright install chromium` first, auto-starts dev server
npx vitest run src/stores/workspace.spec.ts            # run a single unit test file
npx vitest run -t "name fragment"                      # run unit tests by name
npx playwright test tests/e2e/smoke.spec.ts            # run a single e2e spec
```

There is no separate lint step — type safety is enforced through `vue-tsc` in `npm run build`. Unit specs live next to their source (`*.spec.ts`); e2e specs live in `tests/e2e/` and are excluded from vitest via `vite.config.ts`.

## Architecture

Vue 3 (`<script setup>`) + Vite + TypeScript + Pinia + Vue Router + **Naive UI**. Code is organized **by feature/surface**, not by file type: the three product areas are `workspace` (the agent chat), `knowledge` (RAG knowledge bases), and `tickets` (work orders), each with its own view, components, store, API module, and types.

`@/*` is aliased to `src/*` (configured in both `vite.config.ts` and `tsconfig.json`).

### The chat SSE pipeline (the core flow)

This is the heart of the app and spans several files. The backend streams a custom SSE protocol; do not assume `EventSource` semantics:

1. `api/chat.ts` `streamChat()` issues a **POST** to `/api/chat/stream` and reads `response.body` as a `ReadableStream` (EventSource can't POST). Bytes are decoded incrementally and fed to `utils/sse.ts` `parseSseChunk()`, which buffers across chunks and splits on `\n\n` into `{ event, data }` events.
2. `stores/workspace.ts` consumes those events and mutates the active assistant message. Event names are a **contract with the backend** (`agent.stream.GraphEventAdapter`): `route` | `thought` | `action` | `observation` | `answer` | `done` | `error`. `answer` chunks append to `content`; `route`/`thought`/`action`/`observation` push into the message's `process[]` trace; `error`/`done` set `status`. Changing any event name requires a matching backend change.
3. `components/workspace/ChatProcessTrace.vue` renders the `process[]` trace. It pairs each `action` with its following `observation`, and decodes the backend's action encoding `"toolName | arguments"` (split on the first `|`).

Streaming is cancellable: the store holds an `AbortController`; `stopStreaming()` aborts it and the catch block treats `AbortError` as a clean stop. A new `sendMessage` aborts any in-flight stream first.

Side effect worth knowing: as `answer` text streams in, `utils/extractors.ts` `extractOrderNo()` (regex `WO\d{11}`) scans it and, on a match, auto-populates the session's ticket context — this is how the chat links to the tickets panel.

### API layer & response envelope

Non-streaming calls go through `api/http.ts` `requestJson<T>()`, which unwraps the backend's standard envelope `ResultEnvelope<T> = { code, msg, data }` (`types/api.ts`). **A response is only successful when `code === 200`** — otherwise it throws `payload.msg`, regardless of HTTP status. When editing API modules, return `data` payloads through this helper rather than re-implementing fetch.

Two cases bypass `requestJson` deliberately: chat streaming (raw `ReadableStream`) and document upload in `api/knowledge.ts` (multipart `FormData`).

All requests use relative `/api/...` paths; the dev server proxies them to `decision-app` on `:8080` (`vite.config.ts`). In production the frontend must be served behind a proxy that maps `/api` to the backend.

### State, theming, and global UI APIs

- **Pinia stores use the Options style** (`state`/`getters`/`actions`), not the setup-function style. Match this when adding stores. Stores hold the per-feature state; `workspace` additionally owns the session list and chat lifecycle.
- **Theme**: `stores/theme.ts` supports `light` | `dark` | `auto`, persists to `localStorage`, and drives the page by setting `document.documentElement.dataset.theme`. `auto` follows the OS via `matchMedia`. `useThemeStore().init()` is called once in `main.ts`.
  - Naive UI theme overrides live in `src/theme/index.ts` and **must stay in sync with the CSS custom properties in `src/styles/tokens.css`** (the file header warns about this) — colors are duplicated across both. Edit both together.
- **Global feedback APIs**: Naive UI's `useMessage`/`useDialog`/`useNotification`/`useLoadingBar` are exposed on `window.$message` etc. by `providers/MessageApiSetup.vue` (mounted inside the provider tree in `providers/AppProviders.vue`) and typed in `src/env.d.ts`. Call them as `window.$message?.info(...)` from anywhere, including non-component code.

### Markdown rendering (XSS-relevant)

`utils/markdown.ts` renders assistant content with `marked` + a hand-registered subset of `highlight.js` languages. The custom renderer **escapes raw HTML blocks** (`html()` → `escapeHtml`) so model output cannot inject markup. Preserve this escaping behavior when touching markdown rendering; assistant content is untrusted.

## Conventions

- **Preserve Chinese UI strings and comments.** User-facing text and many code comments are Chinese and often encode product/business intent — don't translate or strip them when editing.
- Components are `<script setup lang="ts">` + scoped `<style>` using the `--color-*` / `--space-*` CSS tokens from `src/styles/`. Avoid hardcoding palette/spacing values.
- The router (`router/index.ts`) is flat: `/workspace` (default), `/knowledge`, `/tickets`, and a catch-all. Each route's `meta.title` is the Chinese page title.
- Design/spec docs for in-progress work live under `docs/superpowers/{plans,specs}/`.
