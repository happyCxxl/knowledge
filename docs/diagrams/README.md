# 系统图维护说明

本目录存放 knowledge 的系统图，由 dsh 加载 Archify skill 生成。
图是「手写 JSON 规格 → 确定性编译 HTML」，不是从代码自动抽取。

## 文件清单

| 文件                          | 类型           | 说明                                               |
|-------------------------------|----------------|----------------------------------------------------|
| `knowledge.architecture.json` | 规格源（真源） | 系统架构图：8 组件 + 9 连接 + 信任边界             |
| `knowledge.architecture.html` | 产物           | 自包含交互 HTML（搜索 / 路径 / 导出 PNG/SVG/WebM） |
| `knowledge.dataflow.json`     | 规格源（真源） | 文件处理链数据流图：B01→B02→B03→B04→B05→B07→B08    |
| `knowledge.dataflow.html`     | 产物           | 自包含交互 HTML                                    |

> 只维护 JSON；HTML 永远从 JSON 重新编译，不要手改 HTML。

## 架构依据

图基于 `../implementations/知识库-整体架构落地设计.md`（架构唯一实现口径）绘制，
覆盖四模块（api/biz/worker/common）、六 Port、任务衔接（DB 账本 + Redis 叫号器）、
外部依赖（MySQL / Redis / Milvus / 模型网关 / 文件服务）。

## 更新方式

### 方式一：让 dsh 调用 skill（推荐，适合大改）

```
用 archify skill 更新系统架构图：新增了 XXX 模块 / 改了 YYY 流程
```

### 方式二：直接编辑 JSON（适合小改）

加节点 / 改标签 / 删边后，重新校验并交付：

```bash
node <archify>/bin/archify.mjs validate architecture knowledge.architecture.json --quality showcase --json
node <archify>/bin/archify.mjs deliver  architecture knowledge.architecture.json knowledge.architecture.html --quality showcase --json
```

其中 `<archify>` 是 dsh 插件里 Archify skill 的根目录，例如：

```
C:\Users\LENOVO\.dsh\profiles\web\node_modules\@tt-a1i\archify-dsh\skills\archify
```

校验要求 showcase 通过：9 项 artifact 检查、0 错 0 警告；失败时按回执里的
`diagnostics[].supportedFixes` 只改被指出的 `subject`，不要整图重写。

### 方式三：评审变更（Delta 对比）

架构发生变化时，用旧 / 新两份 JSON 生成 Before/Delta/After 变更图，供 MR 评审：

```bash
node <archify>/bin/archify.mjs compare architecture 旧.json 新.json 变更对比.html --json
```

## 不装 skill 也能用

看图、改 JSON、重生成 HTML 对工具的要求不同：

| 角色                       | 需要装什么                                       |
|----------------------------|--------------------------------------------------|
| 只想看图                   | 什么都不装（浏览器打开 HTML 即可，自包含单文件） |
| 只想改 JSON 内容           | 什么都不装（纯文本编辑器）                       |
| 想把 JSON 重新编译成 HTML  | Node ≥ 18 + 克隆 archify 仓库跑 CLI              |
| 想用 dsh 对话式生成 / 更新 | 装 `@tt-a1i/archify-dsh` + 重启 dsh              |

### 只重生成 HTML（不装 dsh 插件）

Archify CLI 零依赖，克隆仓库即可用，只需 Node ≥ 18：

```bash
git clone https://github.com/tt-a1i/archify
cd archify
node bin/archify.mjs validate architecture ../docs/diagrams/knowledge.architecture.json --quality showcase --json
node bin/archify.mjs deliver  architecture ../docs/diagrams/knowledge.architecture.json ../docs/diagrams/knowledge.architecture.html --quality showcase --json
```

> 校验与交付规则和 dsh skill 完全一致（showcase 9 项、0 错 0 警告）。

### 装 dsh 插件（可选）

```bash
dsh plugin --profile web add @tt-a1i/archify-dsh@0.1.0
# 装完重启 dsh 即可用
```

## 维护约定

1. 改架构代码时，**同一 MR 里同步更新本目录的 JSON**，让图跟着代码走。
2. JSON 进 GitLab 评审（小、可 diff）；HTML 是产物，随 JSON 重生成。
3. 图是 agent 按文档 / 代码「人肉」画的快照，不自动同步；代码变了但图没更新就会漂移。
4. 需要视觉检查时，可临时生成截图证据（用完可删）：

   ```bash
   node <archify>/bin/archify.mjs visual-check knowledge.architecture.html --json
   ```

   它会产出 `*.visual-check.*.png` / `*.visual-check.html` / `*.visual-check.json` 副产物。

## 备注

- Archify 的 `sources`（节点挂 `SRC n` + Git 校验文件 / 行号）源码证据功能当前仅支持
  GitHub 公开仓库 URL，本项目为 GitLab 私有，暂不适用。
- 生成工具：`@tt-a1i/archify-dsh@0.1.0`（社区集成，非 DeepSeek 官方产品）。
