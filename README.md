# knowledge

知识库系统：覆盖文档输入、解析、统一文档模型、预处理、切片、向量化、索引构建与检索评测的多阶段流水线。

## 技术栈

- Java 21 / Spring Boot 3.2.5 / Maven 多模块
- MyBatis-Plus 3.5.5 / MySQL 8 / Redis 7 / Milvus 2.5.5
- 基础设施编排：Docker Compose（MySQL + Redis + Milvus standalone + etcd + MinIO）

## 模块

| 模块               | 说明                                           |
|--------------------|------------------------------------------------|
| `knowledge-api`    | 接口契约模块（预留）                           |
| `knowledge-biz`    | 应用入口与业务接口层（控制器 / 服务 / 持久化） |
| `knowledge-common` | 领域模型、实体、枚举、异常与工具               |
| `knowledge-worker` | 处理引擎：解析 / 切片 / 向量化 / 索引构建      |

## 本地运行

前置：已安装 JDK 21、Maven 3.9+、Docker Desktop。

1. 启动基础设施（首次会自动建库建表）：

   ```powershell
   cd docker
   docker compose up -d
   ```

2. 构建并启动应用（端口 4388）：

   ```powershell
   mvn package -DskipTests
   java -jar knowledge-biz\target\knowledge-biz-5.1.0.jar
   ```
