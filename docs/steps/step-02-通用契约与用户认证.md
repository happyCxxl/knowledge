# 阶段 2：通用契约与用户认证（实施文档）

> 约定：每个实施阶段在 `docs/steps/` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                               |
|----------|----------------------------------------------------|
| 阶段     | 2（通用契约与用户认证）                            |
| 状态     | 已完成（已提交：`feat(auth): 通用契约与用户认证`） |
| 方案依据 | 自建（登录/鉴权自实现，无平台共享能力假设）        |

## 1. 目标与范围

**目标**：common 契约层（R/错误码/异常/用户模型）+ knowledge-auth 业务模块（登录/注册/JWT/安全过滤链）。

1. common：`R<T>` 统一响应（SUCCESS=0）、`ErrorCode`（40001 参数 / 401xx 认证 / 40500 系统兜底）、`KnowledgeException`/`ThrowUtil`、`KnowledgeUser`/`SecurityUtils`、`User` 实体（无租户）、认证 DTO（LoginRequest/RegisterRequest/LoginVO）；
2. knowledge-auth：`AuthController`（注册/登录）、`AuthService(+Impl)`（BCrypt）、`JwtUtil`（hutool-jwt HS256 + 显式过期校验）、`JwtAuthFilter` + `SecurityConfig`（放行 /auth/** 与 swagger 路径）；
3. biz 配套：`KnowledgeMetaObjectHandler`（公共字段自动填充、登录人落账）、`GlobalExceptionHandler`（R 包装）、`@MapperScan` 增加 auth.mapper、application.yml 增 jwt 配置；
4. SQL `stage-02-用户认证.sql`（kb_user），已执行落库。

## 2. 本阶段决策（记录）

| 编号 | 决策                                                                                                              | 依据     |
|------|-------------------------------------------------------------------------------------------------------------------|----------|
| D2-1 | JWT 无状态：HS256、secret 走配置（knowledge.auth.jwt.secret）、有效期 7 天；hutool 校验不含过期判定，显式比对 exp | 实测修正 |
| D2-2 | 无登出接口：客户端丢弃令牌即登出；令牌黑名单后置                                                                  | 用户拍板 |
| D2-3 | 错误码分段登记：40001 / 40101~40103 / 40500                                                                       | 用户拍板 |
| D2-4 | 认证不依赖外部服务；提交人/操作人统一从 SecurityUtils 取当前登录用户                                              | 用户拍板 |

## 3. 任务清单

1. common 契约层（R、ErrorCode、异常×2、用户模型×2、User 实体、DTO×3）；
2. knowledge-auth 模块（11 主文件 + 2 测试文件）；
3. biz 配套（MetaObjectHandler、GlobalExceptionHandler、MapperScan、yml）；
4. SQL stage-02 执行落库。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + auth 8 = 12 用例）
- [x] kb_user 表落库
- [x] 登录/注册链路单测覆盖（AuthServiceTest 5 + JwtUtilTest 3 + ThrowUtilTest 3）

## 5. 执行记录

| 任务              | 执行结果 | 备注                               |
|-------------------|----------|------------------------------------|
| 契约层 + 认证模块 | 完成     | 提交 `eaaaae7`（32 文件 +1124 行） |
| SQL               | 完成     | kb_user 落库                       |

## 6. 下一步预告

阶段 3：核心领域模型与知识库管理闭环。
