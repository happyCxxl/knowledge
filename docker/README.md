# 本地开发基础设施（Docker）

本目录提供本地开发所需的全部基础设施编排，镜像均来自官方仓库。

## 前置

- 已安装 Docker Desktop 并启动引擎（引擎图标变绿）。
- 首次安装 Docker 前需先启用 WSL2：管理员 PowerShell 执行 `wsl --install` 并重启。

## 启动

```powershell
cd E:\workbuddy\knowledge\docker
docker compose up -d
```

首次启动会自动拉取镜像并按文件名序执行 `knowledge-biz/src/main/resources/sql/` 目录下的全部迁移脚本
（`step-00-建库.sql` ~ `step-14-检索与评测.sql`，每个脚本对应一个实施环节，各自自包含 `USE knowledge`），
只会在**数据卷为空**时执行一次；改了脚本想重来需 `docker compose down -v` 后再 `up -d`。

## 组件与端口

| 组件         | 端口                            | 账号           | 说明                                              |
|--------------|---------------------------------|----------------|---------------------------------------------------|
| MySQL 8      | 3306                            | root / root123 | 首次启动自动建库 `knowledge` + 全部表 + 策略 seed |
| Redis 7      | 6379                            | 无密码         | 任务唤醒队列 / 启动补偿分布式锁                   |
| Milvus 2.5.5 | 19530（gRPC）/ 9091（健康检查） | —              | 向量库；配套 etcd + minio 内部容器                |

与 `application.yml` 的对应关系：

- `spring.datasource.*` → mysql 容器
- `spring.data.redis.*` → redis 容器
- `knowledge.milvus.uri` → milvus 容器（http://localhost:19530）

## 常用命令

```powershell
docker compose ps          # 查看容器状态
docker compose logs -f milvus   # 看某个容器日志
docker compose down        # 停止（数据保留）
docker compose down -v     # 停止并清空数据卷（重置环境）
```
