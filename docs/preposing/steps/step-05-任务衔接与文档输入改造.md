# 阶段 5：任务衔接与文档输入改造（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                                          |
|----------|-------------------------------------------------------------------------------|
| 阶段     | 5（任务衔接与文档输入改造）                                                   |
| 状态     | 已完成（待用户提交）                                                          |
| 方案依据 | 原项目档案（stash：step-03 任务衔接与 B01 改造 / B01 任务衔接与改造实现文档） |

## 1. 目标与范围

**目标**：落地任务唤醒链（Redis 队列）+ 文档输入四项改造的收口。

1. Redis 任务唤醒链：DB 任务表唯一状态账本，Redis List 只当"叫号器"；
2. 提交链收口：幂等并发冲突回放、流式校验、格式白名单——已在阶段 4 落地，本阶段回归确认；
3. 启动补偿 + 低频兜底（默认关）+ RUNNING 孤儿恢复。

**不做什么**：消费侧执行循环与文档解析（阶段 6，本轮只定契约与消息格式）；任务领取（claim）与扫库领批（阶段 6）。

## 2. 本阶段决策（记录）

| 编号 | 决策                                                                                                                | 依据                |
|------|---------------------------------------------------------------------------------------------------------------------|---------------------|
| D5-1 | 任务唤醒选型：Redis List + BRPOP；队列单 key `knowledge:task:queue`，消息 = taskId 纯字符串                         | R3-1/R3-3           |
| D5-2 | 无周期对账：提交/触发时入队 + 消费侧重读 DB 校验 + 启动补偿一次 + 低频兜底可选（默认关）                            | R3-2                |
| D5-3 | 队列组件分层：infra 通用组件（`RedisQueueSupport`/`RedisLock`）+ biz 业务封装（`TaskQueueSupport`：key 与消费契约） | 实施手册 §5 + R3-11 |
| D5-4 | 提交链路维持"手动逐环节"口径：提交不登记任务、不入队；触发接口事务内 save 后 enqueue（阶段 6 落地）                 | R3-2① 修订          |
| D5-5 | 启动补偿阈值 5min；低频兜底 10min、锁 30s（SET NX EX，不引 redisson）；孤儿恢复按环节超时 map                       | R3-9                |
| D5-6 | 任务机制方法按需补齐：本阶段 `listStaleQueued`/`listStaleRunning`/`finish`；claim/领批留阶段 6                      | 裁剪口径            |

## 3. 任务清单

1. infra：`RedisQueueSupport`（leftPush/rightPop）+ `RedisLock`（SET NX EX + Lua 比对释放）；infra pom 加 spring-boot-starter-data-redis；
2. common：`PipelineTaskErrorCode`（EXECUTOR_TIMEOUT，任务错误码与 API 错误码分层）；
3. biz：`TaskQueueProperties`（knowledge.task.*）+ `task/TaskQueueSupport`（enqueue/blockingPop 契约）+ `task/TaskStartupCompensator`（启动补偿 + 孤儿恢复 + 低频兜底默认关）；
4. biz：`KbPipelineTaskDbService` 补 3 方法（条件更新口径）；
5. 配置：application.yml 增 knowledge.task.*；biz pom 加 redis starter；
6. 单测：infra 队列/锁各一、KbPipelineTaskDbServiceImplTest 3 例、TaskStartupCompensatorTest 6 例；
7. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + worker 14 + biz 46 = 77）
- [x] 补投/孤儿恢复/开关/抢锁失败路径单测覆盖
- [ ] 真实冒烟：触发任务后 Redis 队列可见 taskId（随阶段 6 触发接口补验）

## 5. 执行记录

| 任务             | 执行结果                                                                                | 备注                     |
|------------------|-----------------------------------------------------------------------------------------|--------------------------|
| 1 infra 队列组件 | 完成：RedisQueueSupport + RedisLock（2 文件）+ 单测 5 例                                | SET NX EX + Lua 比对释放 |
| 2 任务错误码     | 完成：PipelineTaskErrorCode（裁剪只留 EXECUTOR_TIMEOUT，其余随环节补齐）                | 与 API 错误码分层        |
| 3 队列与补偿     | 完成：TaskQueueProperties/TaskQueueSupport/TaskStartupCompensator（3 文件）             | blockingPop 只定义不调用 |
| 4 DbService      | 完成：listStaleQueued/listStaleRunning/finish                                           | 条件更新（RUNNING 互斥） |
| 5 配置           | 完成：application.yml + biz pom redis 依赖                                              | lettuce，不引 redisson   |
| 6 单测           | 完成：新增 14 例；修复 MP 单测三处（TableInfo 初始化/getParamNameValuePairs/getSqlSet） | 77/77 全绿               |

## 6. 下一步预告

阶段 6：文档解析（消费循环：BRPOP 叫醒 + 扫库领批 + claim + 两级路由解析 + 产物落库）。
