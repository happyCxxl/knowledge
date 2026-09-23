# 阶段 1：基础设施模块 knowledge-infra（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                              |
|----------|---------------------------------------------------|
| 阶段     | 1（基础设施模块 knowledge-infra）                 |
| 状态     | 已完成（已提交：`feat(infra): 数据与规范层模块`） |
| 方案依据 | 原项目档案（stash：step-00/01）                   |

## 1. 目标与范围

**目标**：新建 `knowledge-infra` 数据与规范层模块——公共字段基类、通用数据访问基类与 MyBatis-Plus 统一配置，供上层模块复用。

1. 公共字段基类：`BaseInfo`（逻辑删除 + 创建/更新留痕）、`BaseCreateInfo`（仅创建留痕）；
2. 通用数据访问基类：`InfraBaseMapper<T>`（继承 MP BaseMapper）、`InfraDbService<T>`/`InfraDbServiceImpl<M,T>`；
3. `MybatisPlusAutoConfiguration`：分页插件 + MySQL 方言；
4. 自动装配清单 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`；
5. `../实施手册.md` 落位（模块架构 + 实施顺序 + 八步操作流程）。

## 2. 本阶段决策（记录）

| 编号 | 决策                                                                                  | 依据      |
|------|---------------------------------------------------------------------------------------|-----------|
| D1-1 | infra 零依赖（不依赖任何业务模块）                                                    | 架构分层  |
| D1-2 | 自动装配走 `AutoConfiguration.imports` 机制（保留，不用 @Import）                     | 用户拍板  |
| D1-3 | MyBatis-Plus 3.5.4 → 3.5.5（与 Boot 3.2 有两处已知不兼容）；mybatis-spring 钉版 3.0.3 | 实测修复  |
| D1-4 | Druid 1.2.23 连接池                                                                   | 用户拍板  |
| D1-5 | 建库脚本更名 `stage-00-建库.sql`（与阶段式命名一致）                                  | D0-2 延续 |

## 3. 任务清单

1. knowledge-infra 模块（pom + 基类×5 + 配置 + 自动装配清单）；
2. 实施手册 v1（模块架构、19 阶段路线图、八步流程、自实现清单、提交约定）；
3. root pom 增加模块；api/common pom 依赖调整。

## 4. 验收标准

- [x] 阶段清单内文件编译零错误
- [x] 模块依赖方向：common → infra 单向

## 5. 执行记录

| 任务       | 执行结果 | 备注                              |
|------------|----------|-----------------------------------|
| 模块与基类 | 完成     | 提交 `6d233cd`（14 文件 +323 行） |
| 实施手册   | 完成     | 同批提交                          |

## 6. 下一步预告

阶段 2：通用契约与用户认证（common 契约层 + knowledge-auth 模块）。
