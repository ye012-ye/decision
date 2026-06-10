# Repository Guidelines

## Project Structure & Module Organization

This repository combines a multi-module Spring Boot backend with a standalone Vue frontend.

- `pom.xml` is the Maven aggregator for `decision-app` and `decision-mcp-server`.
- `decision-app/` is the main Spring Boot 3 AI agent service. Java code lives in `src/main/java/com/ye/decision`, resources in `src/main/resources`, and tests in `src/test/java`.
- `decision-mcp-server/` is an independent Spring AI MCP server for database tools.
- `decision-web/` is the Vue 3, Vite, TypeScript frontend. Source files are under `src/`, unit tests sit beside code as `*.spec.ts`, and Playwright tests are in `tests/e2e/`.
- `docs/` contains SQL references, design specs, plans, and technical notes.

## Build, Test, and Development Commands

From the repository root:

```powershell
rtk .\mvnw.cmd clean package -DskipTests
rtk .\mvnw.cmd -pl decision-app -am spring-boot:run
rtk .\mvnw.cmd -pl decision-mcp-server -am spring-boot:run
rtk .\mvnw.cmd -pl decision-app test
rtk .\mvnw.cmd -pl decision-app "-Dtest=WorkOrderServiceTest" test
```

From `decision-web/`:

```powershell
rtk npm run dev
rtk npm run build
rtk npm run test
rtk npm run test:e2e
```

Start `decision-mcp-server` before `decision-app` when validating MCP-backed tools.

## Coding Style & Naming Conventions

Backend code uses Java 17, Spring Boot conventions, Lombok, and package names under `com.ye.decision` or `com.ye.mcp`. Use `PascalCase` for classes, `camelCase` for methods and fields, and keep controllers, services, tools, domain objects, and config in their existing package families. Prefer extending `decision.*` configuration keys in `bootstrap.yaml`.

Frontend code uses Vue single-file components, TypeScript, Pinia, and the `@` alias for `decision-web/src`. Name Vue components in `PascalCase`; name stores and utilities by feature.

## Testing Guidelines

Backend tests use JUnit, Spring Boot Test, H2, Spring Security Test, and Spring Kafka Test. Some full-module tests depend on Redis, Milvus, RabbitMQ, MySQL, Nacos, or DashScope, so prefer focused test classes for local verification when infrastructure is unavailable.

Frontend unit tests use Vitest with `happy-dom`; e2e tests use Playwright. Keep unit specs near source files as `*.spec.ts`.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commit style: `feat:`, `fix:`, `chore:`, and `test:`. Keep commit subjects concise and scoped when useful, for example `feat(web): add route guard`.

Pull requests should describe the change, list verification commands, call out required external services, and include screenshots for visible UI changes.

## Agent-Specific Instructions

Codex agents in this workspace must follow `C:\Users\test\.codex\RTK.md`: prefix shell commands with `rtk`. Do not modify unrelated local changes; this repository may have work in progress.
