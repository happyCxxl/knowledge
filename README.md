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

| 模块                    | 职责                                                                                                  | 依赖                                            |
|-------------------------|-------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| `knowledge-common`      | 最底层：领域模型与实体、枚举、错误码与异常、统一返回契约、用户模型与安全上下文、自定义注解、工具类    | —                                               |
| `knowledge-infra`       | 基础设施：MyBatis-Plus 统一配置与数据访问基类、Redis 队列 / 锁 / 计数器、限流切面与令牌桶（bucket4j） | common                                          |
| `knowledge-auth`        | 认证与用户：登录 / 注册、用户表、JWT、Spring Security 过滤链                                          | common、infra                                   |
| `knowledge-file-center` | 文件服务：上传 / 下载、文件档案表、内容寻址对象存取（存储提供者可替换）                               | common、infra                                   |
| `knowledge-model`       | 模型能力：模型目录、网关端口、供应商实现（DashScope）                                                 | common                                          |
| `knowledge-vector`      | 向量库封装：Milvus 客户端生命周期、集合管理、查询与口径钉版                                           | common                                          |
| `knowledge-worker`      | 文档处理引擎：输入校验、解析、组装、预处理、切片、向量化、索引                                        | common、file-center、model、vector              |
| `knowledge-biz`         | 应用入口与业务接口层：控制器、服务编排、任务消费、索引与检索评测                                      | auth、common、file-center、infra、model、worker |

依赖方向单一：`common` 不依赖任何模块（由 `ModuleDependencyGuardTest` 锁定），`infra` 只依赖 `common`；业务模块用谁就声明谁，不依赖传递引入。

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
> 改动跑同一套门禁，**并逐步打印进度**（`[i/N]` 横幅 + 每步耗时 + 失败定位与复跑命令，子命令输出
> 照旧实时透传），推送过程随时可见进度；提交前钩子只做就地格式化（`tools/frontend/README.md`）。

构建环境的两处约定都在仓库里，不用每人配环境变量：

- **`.mvn/jvm.config`** 把 Maven 所用的 JVM 的 `stdout.encoding` / `stderr.encoding` 固定为 UTF-8。
  Windows 中文环境（`native.encoding` = GBK）下 JDK 默认按 GBK 写标准输出，而 javac 的中文告警会
  走进这个通道 —— 在 UTF-8 的终端里（Git Bash、IDE 终端、钩子）就显示成乱码。该文件**不支持注释**，
  所以说明写在这里。
- **父 pom 的 `maven-compiler-plugin` 配了 `<proc>full</proc>`**，显式打开注解处理，消掉 JDK 21 起
  「隐式启用注解处理」的告警（项目靠 classpath 发现 Lombok、没有显式声明处理器）。**只能显式配插件**：
  该插件的 `proc` 参数没有 user property（`release` 同理，各模块才各自手写 `<release>21</release>`），
  写成属性会被静默忽略；放在 `<build><plugins>` 而不是 `pluginManagement` —— 后者到不了没声明该插件的模块。

## 文档

| 文档                             | 内容                           |
|----------------------------------|--------------------------------|
| `tools/backend/README.md`        | 后端静态检查口径与交付自查清单 |
| `tools/frontend/README.md`       | 前端检查的组成与分工           |
| `web/docs/前端开发规范.md`       | 前端实现规范                   |
| `web/docs/delivery-checklist.md` | 前端交付自查清单               |
