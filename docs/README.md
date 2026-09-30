# 知识库平台 · 页面梳理与全局约定

本文档是 `docs/` 的**唯一根文档**，承担两件事：说明页面文档怎么组织（含索引与维护约定）、记录跨所有页面的全局口径。页面独有的内容一律放在各自页面目录里，这里不复述。

## 页面文档怎么组织

按**功能单元**记录平台每个页面：一个功能单元一个目录，目录下按内容分工拆成多份文档：

| 文档             | 回答什么                                                           |
|------------------|--------------------------------------------------------------------|
| `README.md`      | 这页是什么：定位、路由与视图、涉及接口、文档导航、待定项、变更记录 |
| `01-页面功能.md` | 给谁用、界面上能做什么、交互与状态                                 |
| `02-接口交互.md` | 前端在什么时机调哪个接口、请求与响应字段、错误码                   |
| `03-接口逻辑.md` | 后端拿到请求之后做了什么（校验、落表、副作用、边界）与涉及的表     |
| `04-自测清单.md` | 改完之后手工要验哪些点、期望结果                                   |

目标是：只看文档就能回答「这个按钮点下去，数据是怎么走到库里的」，不必回头翻代码。

## 维护约定

- **代码是实况，文档跟着代码追。** 页面扩功能、改交互、换接口之后招呼一声更新对应文件，不靠人肉记得「改了代码要同步文档」。
- **不写行号**，只写 `文件:方法名`。行号一动功能就失效，符号名才是稳定的。
- **跨页面的同一份口子只在本文档写一次**，页面文件只写本页特有的东西，遇到通用规则引用而不复述。
- **页内按归属规则分文档**：接口路径 / 请求体 / 响应字段 / 错误码 → `02-接口交互.md`；校验顺序 / 落库 / 副作用 / 边界条件 → `03-接口逻辑.md`；两边都受影响时各写一句并互相引用，同样不复述。
- **类型定义不抄全文**，只写契约要点与**与后端不一致的地方**。
- **变更记录记在页面目录的 `README.md`**（页面级时间线），改动时追加一行；不分散到各分文档，否则同一处改动会散落多份文件。
- 页面 `README.md` 里的「待定项」是梳理过程中读出来的口径问题，留给迭代时顺手决定，不作为结论。

## 页面索引

| 序 | 功能单元                         | 路由                            | 前端视图                                                | 对应后端 Controller（初判）                                                                                                                                                                                                                                              | 状态   |
|----|----------------------------------|---------------------------------|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| 壳 | 布局壳（菜单 / 返回 / 退出登录） | 无独立路由                      | `../web/src/layouts/AppLayout.vue`                      | —                                                                                                                                                                                                                                                                        | 待梳理 |
| 01 | 认证（登录 / 注册 / 改密）       | `/login`                        | `../web/src/views/login/LoginView.vue`                  | `AuthController`、`UserController`（个人信息与改密）                                                                                                                                                                                                                     | 已梳理 |
| 02 | 工作台                           | `/home`                         | `../web/src/views/home/HomeView.vue`                    | `HomeController`                                                                                                                                                                                                                                                         | 待梳理 |
| 03 | 知识库列表                       | `/knowledge-base`               | `../web/src/views/knowledge-base/KnowledgeBaseView.vue` | `KnowledgeBaseController`                                                                                                                                                                                                                                                | 待梳理 |
| 04 | 切片阶段（执行链）               | `/knowledge-base/:id/stages`    | `../web/src/views/knowledge-base/PipelineStageView.vue` | `KnowledgeFileInputController`、`KnowledgeFileParseController`、`KnowledgeFileStructureController`、`KnowledgeFilePreprocessController`、`KnowledgeFileChunkController`、`KnowledgeFileEmbedController`、`StageContentController`、`LineageController`、`FileController` | 待梳理 |
| 05 | 索引与发布                       | `/knowledge-base/:id/index`     | `../web/src/views/knowledge-base/IndexBuildView.vue`    | `KnowledgeFileIndexController`                                                                                                                                                                                                                                           | 待梳理 |
| 06 | 检索评测                         | `/knowledge-base/:id/retrieval` | `../web/src/views/knowledge-base/RetrievalEvalView.vue` | `RetrievalController`                                                                                                                                                                                                                                                    | 待梳理 |
| 07 | 策略管理                         | `/strategy`                     | `../web/src/views/strategy/StrategyManagementView.vue`  | `StrategyVersionController`                                                                                                                                                                                                                                              | 待梳理 |
| 08 | 用户管理（仅管理员）             | `/user`                         | `../web/src/views/user/UserManagementView.vue`          | `UserController`                                                                                                                                                                                                                                                         | 待梳理 |

顺序按用户使用动线排。一个路由里若装了互相独立的功能，会拆成多份文件；「Controller（初判）」是文件归属的初步判断，逐页梳理时以实际调用为准，可能修正。

## 文档入口

- 01 认证（登录 / 注册 / 改密）→ `pages/登录认证/README.md`
- 其余功能单元（壳、02~08）梳理后在此补上

## 全局约定

跨所有页面的同一份口径。页面文件只引用本章，不复述其中的内容。

### 一、响应体与 HTTP 状态码

后端统一响应体 `R<T>`（`../knowledge-common/src/main/java/com/knowledge/common/dto/response/R.java`）：

| 字段        | 含义                                 |
|-------------|--------------------------------------|
| `code`      | 0 表示成功；**错误语义全部由它表达** |
| `msg`       | 提示语，成功的默认值是「操作成功」   |
| `data`      | 业务数据，失败时为 `null`            |
| `timestamp` | 毫秒时间戳                           |

- **HTTP 状态码恒为 200**（网络层故障除外）。业务错误、参数错误、未认证、无权限一律走 200 + `R.code`。直接原因是浏览器会把裸 4xx 响应渲染成 `HTTP ERROR 403` 页面，前端也拿不到可判断的 code。
- 三处出口共同保证这个口径：`GlobalExceptionHandler`（Controller 抛出的异常）、`RestAuthenticationEntryPoint`（未登录）、`RestAccessDeniedHandler`（已登录但无权限）。后两者位于 `knowledge-auth` 的 `config` 包，因为它们发生在 Controller 之前，不经过全局异常处理器。
- 前端对应类型是 `../web/src/types/response.ts` 的 `R<T>`，分页数据对应 `PageResult<T>`。
- `R.failed(msg)`（`code=1`）是遗留的通用失败码，**现有代码无人调用**；业务失败一律抛 `KnowledgeException`。

### 二、错误码

登记在 `../knowledge-common/src/main/java/com/knowledge/common/error/ErrorCode.java`，**新错误码必须在此登记**。分段：`40001` 参数、`401xx` 认证、`402xx` 限流、`404xx` 业务、`40500` 系统兜底。下表「默认提示语」列为源码中的原文，前端未给自定义消息时展示它。

| code  | 常量                                   | 默认提示语                                                     |
|-------|----------------------------------------|----------------------------------------------------------------|
| 40001 | `PARAM_INVALID`                        | 参数错误                                                       |
| 40101 | `UNAUTHORIZED`                         | 未认证或令牌无效                                               |
| 40102 | `USERNAME_EXISTS`                      | 用户名已存在                                                   |
| 40103 | `LOGIN_FAILED`                         | 用户名或密码错误                                               |
| 40104 | `FORBIDDEN`                            | 权限不足，无法访问该功能                                       |
| 40105 | `USER_NOT_FOUND`                       | 用户不存在                                                     |
| 40106 | `ROLE_INVALID`                         | 角色不合法                                                     |
| 40107 | `USER_SELF_OPERATION_FORBIDDEN`        | 不能对当前登录账号执行该操作                                   |
| 40108 | `LAST_ADMIN_FORBIDDEN`                 | 系统需保留至少一个启用的管理员账号                             |
| 40109 | `USER_STATUS_INVALID`                  | 用户状态不合法                                                 |
| 40110 | `OLD_PASSWORD_MISMATCH`                | 当前密码不正确                                                 |
| 40111 | `LOGIN_TOO_FREQUENT`                   | 登录尝试过于频繁，请稍后再试                                   |
| 40201 | `RATE_LIMITED`                         | 请求过于频繁，请稍后再试                                       |
| 40401 | `KB_NOT_FOUND`                         | 知识库不存在                                                   |
| 40402 | `KB_STATUS_ILLEGAL`                    | 知识库状态不合法                                               |
| 40410 | `FILE_NOT_FOUND`                       | 文件不存在                                                     |
| 40420 | `REQUEST_ID_MISSING`                   | 缺少幂等键                                                     |
| 40421 | `KB_NOT_ACTIVE`                        | 知识库未启用，不可提交文档                                     |
| 40431 | `TASK_ALREADY_PENDING`                 | 任务进行中，请勿重复触发                                       |
| 40432 | `FILE_RESULT_NOT_FOUND`                | 文件结果不存在                                                 |
| 40433 | `STRATEGY_VERSION_NOT_FOUND`           | 策略版本不存在或未启用                                         |
| 40434 | `EMBED_UPSTREAM_MISSING`               | 切片产物不存在，请先触发切片                                   |
| 40435 | `EMBED_MODEL_INCOMPATIBLE`             | 切片最大片长超过模型窗口，请更换模型或调整切片策略             |
| 40437 | `PARSE_ALREADY_SUCCEEDED`              | 解析已成功或部分成功，无需重跑                                 |
| 40441 | `INDEX_VERSION_NOT_FOUND`              | 索引版本不存在                                                 |
| 40442 | `INDEX_ONLINE_DELETE_FORBIDDEN`        | 在线发布版本禁止删除                                           |
| 40443 | `INDEX_BUILDING_CONFLICT`              | 该索引版本构建中，禁止重复操作                                 |
| 40444 | `INDEX_COMBO_INCOMPLETE`               | 组合产物不完整，无法构建                                       |
| 40446 | `INDEX_NOT_PUBLISHED`                  | 未发布任何索引版本                                             |
| 40447 | `INDEX_COLLECTION_SCHEMA_MISMATCH`     | 索引集合结构与声明不一致，请重建集合                           |
| 40448 | `INDEX_COMBO_SNAPSHOT_LEGACY`          | 索引组合快照缺失环节策略维度，疑似旧口径数据，请废弃重灌后重试 |
| 40449 | `INDEX_FROZEN_SCOPE_PUBLISH_FORBIDDEN` | 指定文件范围的评测冻结集禁止发布/回退                          |
| 40450 | `RETRIEVAL_RULE_NOT_FOUND`             | 检索规则不存在                                                 |
| 40451 | `RETRIEVAL_CAPABILITY_LOCKED`          | 该检索能力尚未启用                                             |
| 40452 | `STRATEGY_BOUND_DELETE_FORBIDDEN`      | 策略已被知识库绑定，禁止删除（可停用代替）                     |
| 40500 | `SYSTEM_ERROR`                         | 系统异常，请稍后重试                                           |

### 三、前端请求层

`../web/src/api/http.ts` 是全站唯一的 axios 实例；页面不直接用它，而是经 `web/src/api/*.ts` 里按领域分的薄封装调用。

- `baseURL: ''`（同源，开发期靠 Vite 代理转发）、`timeout: 15000`。
- 令牌键 `knowledge-token`，读取顺序 `localStorage ?? sessionStorage`；取到就加 `Authorization: Bearer <token>`。
- 响应拦截器统一解包与提示：

| 情况             | 拦截器行为                                                                     |
|------------------|--------------------------------------------------------------------------------|
| `code === 0`     | 把 `response.data` 替换成 `body.data`，页面拿到的是业务数据本身，不是 `R` 包装 |
| `code === 40101` | 清令牌 → 携 `redirect` 参数硬跳 `/login`（当前路径编码后作为参数值）           |
| `code === 40500` | 提示「系统异常，请稍后重试」                                                   |
| 其他非 0         | 提示 `body.msg`                                                                |
| HTTP 401 / 403   | 兜底按未认证处理，同样回登录页（否则页面会卡在拿不到数据的裸错误页）           |
| 其他 HTTP 错误   | 提示「网络异常，请检查服务是否可用」                                           |

- 因此**页面里 `catch {}` 留空是正常写法**：失败提示由拦截器统一负责，页面只处理成功分支。

### 四、登录态与鉴权

- 令牌是 JWT（Hutool 签发，HS256），载荷为 `sub`（用户 ID）、`username`、`role`、`tv`（令牌版本）、`exp`。**有效期分两档**，登录请求用 `remember` 选档：不勾选「记住我」按 `knowledge.auth.jwt.ttl-days`（当前 1 天），勾选按 `ttl-days-remember`（当前 30 天）。密钥来自 `knowledge.auth.jwt.secret`（当前是占位值 `knowledge-jwt-secret-change-me-in-production`，**上生产必须替换**）。
- 令牌是**无状态**的，签发后服务端无法提前作废单张令牌（只能靠 `token_version` 全量失效），因此短期档是真的到期就要重新登录，不会因为"还在用"而自动续期。
- 前端把令牌存进 localStorage（勾「记住我」）或 sessionStorage，**并把登录响应里的用户字段存成同一档的「用户快照」**（键 `knowledge-user`：`id` / `username` / `displayName` / `email` / `phone` / `status` / `role`）。界面展示与菜单渲染只读快照，**令牌载荷仅用于判断过期**（刷新后除令牌外没有别的凭据，`exp` 只能从令牌取），**真正的鉴权始终在后端**。store 初始化时会直接丢弃过期令牌，避免「看似已登录」导致回不到登录页的死循环。
- 后端 `JwtAuthFilter`（`knowledge-auth` 的 `config` 包）每个请求都过一遍：验签 → 查过期 → **比对令牌版本**（库中当前值 vs 载荷 `tv`）。改密码、改角色、停用、删除都会让该账号已签发的令牌立即失效，代价是每个带令牌请求多一次主键查询。
- **登出没有接口**：客户端丢弃令牌即可（`AppLayout.handleLogout`）。
- 路由守卫（`../web/src/router/index.ts`）：未登录访问受保护页 → 跳 `/login?redirect=<原路径>`；已登录访问 `/login` → 回 `/home`；非管理员访问标了 `meta.adminOnly` 的页 → 回 `/home`。
- `redirect` 参数是登录后跳回原页面的唯一来源（守卫与接口层的 40101 硬跳转都写它），登录页只接受以 `/` 开头且不以 `//` 开头的站内路径，其余一律回落 `/home`——不校验会变成开放重定向。

### 五、权限控制

- `SecurityConfig`（`knowledge-auth` 的 `config` 包）：无状态（STATELESS）、关 CSRF；放行 `/auth/login`、`/auth/register` 与 swagger 路径，**其余请求一律需要认证**。
- 角色控制走方法注解，由 `@EnableMethodSecurity` 开启。`@AdminOnly`（`../knowledge-common/src/main/java/com/knowledge/common/annotation/AdminOnly.java`）以 `@PreAuthorize` 为元注解判 `ROLE_ADMIN`，**逐个标在管理类接口的方法上**（`UserController` 的查询/新增/编辑/删除四个方法即此写法，同控制器下的个人信息接口对本人开放）。新增管理方法必须一并标注，漏标即对普通用户开放。
- 过滤器写入安全上下文的权限串是 `ROLE_<role>`，必须与注解表达式对得上，否则注解永远不通过。

### 六、限流与登录防护

两套机制独立且互补，都落在 Redis 上：

| 机制             | 数什么                   | 维度                    | 触发结果                   |
|------------------|--------------------------|-------------------------|----------------------------|
| **接口级限流**   | 请求次数（不分成败）     | 客户端 IP + 接口名      | `40201 RATE_LIMITED`       |
| **登录失败防护** | 连续失败次数（成功清零） | 账号 + IP 为主，IP 为辅 | `40111 LOGIN_TOO_FREQUENT` |

- 接口级限流：方法上标 `@RateLimit(capacity, periodSeconds, message)`。注解在 `knowledge-common/annotation`（契约），切面与令牌桶实现在 `knowledge-infra/ratelimit`（基础设施，bucket4j，桶存 Redis、跨实例共享，并带 1 小时过期，窗口过后自动回收）。用法示例见 `AuthController.login`。
- 登录失败防护：阈值与窗口由 `knowledge.auth.guard.*` 配置（默认账号+IP 维度 5 次、IP 维度 20 次、窗口 15 分钟）。实现见 `LoginAttemptGuard`。
- **计数对不存在的用户名同样生效**：否则「打存在的账号会锁、打不存在的不会」就是一条账号枚举侧信道，会把登录接口统一 40103 的努力绕开。
- **成功登录只清账号维度，不清 IP 维度**：否则攻击者持有一个有效账号、每隔几次成功登录一次，就能把 IP 计数刷掉，喷洒撞库照样成立。
- **锁定/限流期间不查库、不比对密码**：BCrypt 是故意设计的慢哈希，放任尝试等于给对方一个 CPU 放大器。
- **客户端 IP 只取 `getRemoteAddr()`，不读 `X-Forwarded-For`**：该请求头由客户端自行伪造，无条件信任等于把限流维度交给攻击者。将来前面加了反向代理，需按可信代理白名单解析。

### 七、前后端契约的硬口径

| 口径                  | 说明                                                                                                                                                                                                                                                                           |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Long 按阈值序列化** | `LongIdJsonConfig`：超过 JS 安全整数上界 `2^53-1` 的整数输出字符串，其余保持数字。雪花 ID 因此以字符串下发，计数 / 字节数 / 耗时 / 分页号仍是数字。前端把 ID 当字符串回传即可，Jackson 反序列化到 `Long` 能正确转换。                                                          |
| **逻辑删除**          | `BaseInfo.delFlag` 带 `@TableLogic(value = "0", delval = "1")`，MyBatis-Plus 自动为查询补 `del_flag = '0'`；删除是 UPDATE，不是 DELETE。                                                                                                                                       |
| **公共字段自动填充**  | `KnowledgeMetaObjectHandler` 填 `del_flag`、`create_by`、`create_time`、`update_by`、`update_time`；`create_by` / `update_by` 取当前认证用户名，**无认证上下文时（如注册）为 null**。                                                                                          |
| **主键**              | 雪花算法，`@TableId(type = IdType.ASSIGN_ID)`，由应用生成后显式插入。                                                                                                                                                                                                          |
| **分页**              | 请求参数 `current`（默认 1）、`size`（默认 10）；响应是 MyBatis-Plus 的 `IPage<T>`，前端对应 `PageResult<T>`（`records` / `total` / `size` / `current` / `pages`）。                                                                                                           |
| **密码规则**          | 新密码长度统一 **8-64 位**：注册、管理员新增、管理员重置、本人改密四处同一口径。登录不做长度校验，存量短密码仍可登录。                                                                                                                                                         |
| **码值走枚举**        | 业务代码不写码值字面量：用户状态用 `UserStatus`（1 启用 / 0 停用，前端 `USER_STATUS`）、用户角色用 `UserRole`（ADMIN / USER）、知识库状态用 `KnowledgeBaseStatus`。`of()` 解析空值或未知码值时，**由调用方决定拒绝（写路径抛业务错误码）还是兜底（读路径按最保守语义处理）**。 |
| **接口文档**          | springdoc 注解（`@Operation` / `@Parameter` / `@Tag`）写在 Controller 上；`/swagger-ui.html` 与 `/v3/api-docs/**` 免登录。                                                                                                                                                     |

### 八、开发期联调

- 前端 dev server：`127.0.0.1:5173`（Vite）；后端：`localhost:4388`。
- 跨域靠 Vite 代理（`../web/vite.config.ts`），已配置前缀：`/auth`、`/files`、`/file-results`、`/knowledge-base`、`/strategy-versions`、`^/user/`、`^/home/`。
- **`/knowledge-base`、`/home`、`/user` 既是 SPA 路由又是接口前缀**，靠 `bypass` 按 `Accept` 头区分：含 `text/html` 的导航请求交回 SPA 回退，接口请求才转发后端。
- 新增接口前缀必须同时加代理，否则请求会落到 SPA 回退上——表现为前端拿到一坨 HTML、页面数据全空，而且**控制台不报错**。

### 九、待补

梳理后续页面时一旦遇到就回填本文档：

- 幂等键的传递方式与约束（`REQUEST_ID_MISSING 40420` 已登记，具体约定待梳理「导入文档」相关页面时确认）。
- 文件上传与下载的请求形态（`/files` 前缀，待梳理切片阶段页时确认）。
- 各页面的数据刷新节奏（轮询 / 手动刷新）暂记在各页面文件里，若形成统一约定再上移到这里。

## 已决定的取舍

记在这里是为了避免同样的讨论重复发生；要推翻某条，改这一行即可。

| 事项                                | 结论                    | 理由                                                                                                                                                                        |
|-------------------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 登录会话管理（在线设备列表 + 单踢） | **不做**                | 令牌是无状态的，"看有哪些会话"必须先在服务端存索引（jti + 会话记录 + 每请求多一次校验），成本是二类里最高的一条；而"改密码/停用即全踢"已由 `token_version` 覆盖主要安全诉求 |
| 对接 SSO（AD/LDAP/OAuth）与 MFA     | **不做**                | 平台定位是小范围自建鉴权；引入 SSO 会让注册、用户信息字段这套自助能力全部作废，是方向性替换而非功能增补                                                                     |
| 密码复杂度策略（引 Passay）         | **不做**                | 当前只做长度 8-64 位 + 不得与旧密码相同，零新依赖；内网场景下复杂度规则收益有限                                                                                             |
| 自助注册开关                        | **保持开放**            | 注册即可登录，不做管理员审核；改为"注册即待审"只需把注册时的 `status` 换成 `UserStatus.DISABLED`，随时可切                                                                  |
| 登录失败锁定的维度                  | 账号 + IP 为主、IP 为辅 | 账号全局锁会被人故意输错密码锁死别人的账号（拒绝服务）                                                                                                                      |

## 关联文档

| 文档                          | 内容                               |
|-------------------------------|------------------------------------|
| `../README.md`（仓库根）      | 项目定位、模块划分与依赖、构建命令 |
| `../web/docs/前端开发规范.md` | 前端编码与注释规范                 |
| `../tools/backend/README.md`  | 后端检查组成与注释规范             |
| `../tools/frontend/README.md` | 前端检查的组成与配置归属           |
| `../docker/README.md`         | 中间件编排与数据库迁移脚本         |
