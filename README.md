# knowledge

知识库系统：覆盖文档输入、解析、统一文档模型、预处理、切片、向量化、索引构建与检索评测的多阶段流水线。

## 项目定位

- **单体应用**：后端单进程；前端 `knowledge-web` 配套建设。
- **登录 / 鉴权自建**：用户、令牌、安全配置均在本项目内实现，不假设平台共享能力。
- **无租户概念**：数据模型不含 tenant 语义。
- **不调用外部业务服务**：文件存储、向量库等均为项目自管组件；外部能力仅限开源基础设施（MySQL / Redis / MinIO / Milvus）。
- **按模块拆分，而非分项目**：模块间为工程分层关系，同一构建与部署单元。

## 技术栈

- 后端：Java 21 / Spring Boot 3.2.5 / Maven 多模块
- 存储：MyBatis-Plus 3.5.5 / MySQL 8 / Redis 7 / MinIO / Milvus 2.5.5
- 前端：Vue 3 / Element Plus / Vite（`web/`）
- 基础设施编排：Docker Compose（MySQL + Redis + Milvus standalone + etcd + MinIO），见 `docker/README.md`

## 模块与依赖方向

| 模块                    | 职责                                                                             | 依赖                                     |
|-------------------------|----------------------------------------------------------------------------------|------------------------------------------|
| `knowledge-infra`       | 数据与规范层：实体基类、雪花主键与 ID 序列化约定、DbService 模式、Redis 队列与锁 | —                                        |
| `knowledge-common`      | 领域模型、实体、枚举、错误码、异常、统一返回契约、用户模型与安全上下文工具       | infra                                    |
| `knowledge-auth`        | 认证与用户：登录 / 注册、用户表、JWT、Spring Security 过滤链                     | common、infra                            |
| `knowledge-file-center` | 文件服务：上传 / 下载、文件档案表、内容寻址对象存取（存储提供者可替换）          | common                                   |
| `knowledge-model`       | 模型能力：模型目录、网关端口、供应商实现（DashScope）                            | common                                   |
| `knowledge-vector`      | 向量库封装：Milvus 客户端生命周期、集合管理、查询与口径钉版                      | common                                   |
| `knowledge-worker`      | 文档处理引擎：输入校验、解析、组装、预处理、切片、向量化、索引                   | common、file-center、model、vector       |
| `knowledge-biz`         | 应用入口与业务接口层：控制器、服务编排、任务消费、索引与检索评测                 | auth、common、file-center、model、worker |

## 本地运行

前置：JDK 21、Maven 3.9+、Docker Desktop。

1. 启动基础设施（首次自动建库建表）：

   ```powershell
   cd docker
   docker compose up -d
   ```

2. 构建并启动应用（端口 4388）：

   ```powershell
   mvn verify -DskipTests
   java -jar knowledge-biz\target\knowledge-biz-5.1.0.jar
   ```

> `mvn verify` 在打包之外还会执行静态检查门禁（SpotBugs / Checkstyle / PMD / CPD）；门禁绑在
> `verify` 相位，因此 `mvn package` 不会触发它。推送前钩子（`.husky/pre-push`）对本次推送范围的
> 改动跑同一套门禁；提交前钩子只做就地格式化（`tools/frontend/README.md`）。

## 文档

| 文档                             | 内容                           |
|----------------------------------|--------------------------------|
| `tools/backend/README.md`        | 后端静态检查口径与交付自查清单 |
| `tools/frontend/README.md`       | 前端检查的组成与分工           |
| `web/docs/前端开发规范.md`       | 前端实现规范                   |
| `web/docs/delivery-checklist.md` | 前端交付自查清单               |
