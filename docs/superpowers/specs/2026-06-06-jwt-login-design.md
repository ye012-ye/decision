# Spring Security + JWT 登录设计

- 日期：2026-06-06
- 范围：`decision-app`（后端）+ `decision-web`（前端）
- 状态：已确认设计，待写实现计划

## 1. 目标与背景

给现有平台加一套**登录**能力：

- 前端是独立的 Vue 3 SPA（`decision-web`），后端是纯 API 服务（`decision-app`，端口 8080）。
- 登录页放在 SPA 里；后端提供 JSON 登录接口，签发 **JWT 无状态 token**。
- 用户存 **MySQL 用户表**，密码 **BCrypt**。
- 当前 `decision-app` **没有 Spring Security**，没有用户表。
- 前端通过代理把 `/api` 指向后端（开发 `:5173`→`:8080`，生产同理），对浏览器是**同源**。

确认的决策：

| 维度 | 选择 |
|---|---|
| 登录页位置 | Vue SPA 内新增登录页 |
| 认证机制 | JWT 无状态 token（Bearer，请求头携带） |
| JWT 实现路线 | **路线 A**：`jjwt` 库 + 自定义 `OncePerRequestFilter`（显式、可控、易调试） |
| 用户来源 | MySQL `sys_user` 表 + BCrypt |
| 本期范围 | 仅登录 + 登出（无状态） |

## 2. 登录数据流

```
Vue 登录页 → POST /api/auth/login {username, password}
  → CustomUserDetailsService 按 username 查 sys_user
  → BCryptPasswordEncoder 校验密码
  → JwtService 签发 JWT（subject=username，含 iat/exp）
  → Result.ok(LoginResp{token, username, nickname})

前端把 token 存 Pinia(auth) + localStorage
之后每个请求（含 SSE 的 POST /api/chat/stream）带 Authorization: Bearer <token>
  → JwtAuthenticationFilter 解析+校验 token → 写入 SecurityContext → 放行
  → 校验失败/缺失 → 401

前端遇 401 → 清 token → 跳 /login
登出：前端丢弃 token（无状态；不做服务端黑名单）
```

## 3. 后端设计（`decision-app`）

### 3.1 依赖（`pom.xml`）

- `org.springframework.boot:spring-boot-starter-security`
- `io.jsonwebtoken:jjwt-api`、`jjwt-impl`(runtime)、`jjwt-jackson`(runtime)，版本 `0.12.x`（在根 BOM 里统一管理版本，与现有 `*.version` 属性风格一致）

### 3.2 用户表（`src/main/resources/db/V3__sys_user.sql`）

`sys_user`：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK auto | 主键（与全局 `id-type: auto` 一致） |
| `username` | VARCHAR 唯一 | 登录名 |
| `password` | VARCHAR | BCrypt 密文 |
| `nickname` | VARCHAR | 展示名 |
| `role` | VARCHAR | 角色（向前兼容，本期不按角色鉴权） |
| `status` | TINYINT | 1 启用 / 0 禁用 |
| `create_time` / `update_time` | DATETIME | 时间戳 |

附一个初始账号（如 `admin`，密码用 BCrypt 预生成密文写入 SQL，明文写在文档/注释里便于首次登录）。

> 遵循项目约定 `spring.sql.init.mode: never`：SQL 文件按 `db/V*.sql` 命名作为参考/手动执行，不依赖 Spring 自动建表。

### 3.3 新增类

| 包 / 类 | 职责 |
|---|---|
| `domain/entity/SysUser` | MyBatis-Plus 实体，`@TableName("sys_user")` |
| `mapper/SysUserMapper` | 继承 `BaseMapper<SysUser>`（落在已扫描的 `com.ye.decision.mapper`） |
| `security/JwtService` | 用配置里的密钥签发 / 解析 JWT；暴露 `generateToken(username)`、`parseUsername(token)`、`isValid(token)` |
| `security/CustomUserDetailsService` | 实现 `UserDetailsService`，按 username 查库，封装成 `UserDetails`（含 BCrypt 密文与启用状态） |
| `security/JwtAuthenticationFilter` | 继承 `OncePerRequestFilter`：取 `Authorization: Bearer`，校验后把 `UsernamePasswordAuthenticationToken` 放进 `SecurityContextHolder` |
| `config/SecurityConfig` | 见下 |
| `controller/AuthController` | `POST /api/auth/login`、`GET /api/auth/me` |
| `domain/dto/LoginReq` | `record LoginReq(String username, String password)` + Bean Validation |
| `domain/dto/LoginResp` | `record LoginResp(String token, String username, String nickname)` |
| `security/JwtProperties` | `@ConfigurationProperties("decision.security.jwt")`，绑定 `secret` / `expire-minutes`（沿用项目 `decision.*` 命名空间） |

### 3.4 `SecurityConfig`（`SecurityFilterChain`）

- `csrf().disable()`（token 在请求头，非 Cookie，无 CSRF 面）
- `sessionManagement` → `STATELESS`
- 授权规则：
  - 放行：`POST /api/auth/login`、`/actuator/health`
  - 其余 `/api/**`：`authenticated()`
- `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
- `cors(Customizer.withDefaults())` —— 复用现有 `WebConfig` 的 CORS（确保 `Authorization` 头被允许）
- Bean：`BCryptPasswordEncoder`、`AuthenticationManager`（从 `AuthenticationConfiguration` 取）
- 401 处理：自定义 `AuthenticationEntryPoint`，未认证时返回 `Result.error(401, "未登录")` 的 JSON（与现有信封一致），而非默认 HTML

### 3.5 接口契约

`POST /api/auth/login`
```
请求: { "username": "admin", "password": "..." }
成功: { "code":200, "msg":"success", "data": { "token":"<jwt>", "username":"admin", "nickname":"管理员" } }
失败: { "code":401, "msg":"用户名或密码错误", "data":null }
```

`GET /api/auth/me`（需登录）
```
成功: { "code":200, "msg":"success", "data": { "username":"admin", "nickname":"管理员", "role":"..." } }
```

## 4. 前端设计（`decision-web`）

| 文件 | 改动 |
|---|---|
| `views/LoginView.vue` | 新增登录页：用现有 Naive UI 组件 + `--color-*`/`--space-*` 设计 token，不另起风格；用户名/密码表单，调 `auth.login()` |
| `stores/auth.ts` | 新增 Pinia（**Options 风格**）：`state{ token, user }`、`actions{ login(), logout(), fetchMe() }`；token/user 持久化到 `localStorage`，启动时回填 |
| `api/auth.ts` | `login(username,password)` → `requestJson` 调 `/api/auth/login`；`fetchMe()` 调 `/api/auth/me` |
| `api/http.ts`（`requestJson`） | 自动注入 `Authorization: Bearer <token>`（从 auth store / localStorage 取）；响应 401 → 清 token、跳 `/login` |
| `api/chat.ts`（`streamChat`） | SSE 的 POST 请求头补 `Authorization`；遇 401/未登录抛出可识别错误 |
| `router/index.ts` | 新增 `/login` 路由；全局 `beforeEach` 守卫：除 `/login` 外的路由要求已登录，未登录重定向到 `/login?redirect=...` |
| `layouts/TopBar.vue` | 顶栏显示当前用户昵称 + 登出按钮（调 `auth.logout()` 后跳 `/login`） |

约定：保留中文 UI 文案；登录页文案为中文。

## 5. 安全与配置

- **密钥**：`decision.security.jwt.secret`、`decision.security.jwt.expire-minutes` 写在 `bootstrap.yaml` 作占位默认值，生产由 **Nacos / 环境变量** 覆盖；密钥不硬编码进代码。
- **过期**：默认访问 token 有效期 **120 分钟**（无刷新 token，本期）。
- **密码**：仅存 BCrypt 密文；日志不打印密码/token。
- **保护范围**：除 `/api/auth/login` 与健康检查外，所有 `/api/**`（聊天、工单、知识库）均需登录。

## 6. 测试

后端：
- `JwtServiceTest`：签发→解析往返、过期 token 判失效、被篡改 token 判失效。
- `AuthControllerTest`（MockMvc + Spring Security test）：
  - 无 token 访问受保护接口 → 401
  - 正确账号密码登录 → 返回 token
  - 错误密码 → 401
  - 带合法 token 访问受保护接口 → 200

前端（vitest）：
- `auth` store：`login()` 成功后存 token/user；`logout()` 清空并清 localStorage。
- `requestJson`：带 token 时注入 `Authorization` 头；响应 401 时清 token。
- 路由守卫：未登录访问受保护路由 → 重定向 `/login`。

## 7. 本期不做（YAGNI 边界）

注册、找回密码、token 刷新（refresh token）、Redis 登出黑名单、按角色细分鉴权（`role` 字段先留位）。需要时再单独立项。

## 8. 风险 / 注意点

- **CORS 与 Security 的衔接**：Spring Security 接管过滤链后，必须在 `SecurityConfig` 显式开启 `cors`，否则现有 `WebConfig` 的 CORS 不生效；同源代理下一般无碍，但要保证 `Authorization` 头放行。
- **SSE 鉴权**：`/api/chat/stream` 是 POST + `ReadableStream`，前端能直接加请求头（非 `EventSource`），过滤器照常拦截，无特殊处理。
- **首个账号**：初始 BCrypt 密文需用确定方式生成并记录明文，避免首次无法登录。
- **依赖版本**：`jjwt` 0.12.x 的 API 与 0.11.x 有差异（`Jwts.builder().signWith(key)`、`Jwts.parser().verifyWith(key)`），实现时按 0.12.x 写。
