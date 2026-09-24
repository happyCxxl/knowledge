# 后端静态检查

后端交付自检与构建门禁，替代已下线的 IDEA 无头检测。规则文件与排除清单均在本目录，由根 pom 统一接入（8 个模块生效）。

## 引擎

| 引擎 | 检查内容 | 规则文件 |
|---|---|---|
| SpotBugs（字节码 bug 模式） | 空指针、资源泄漏、equals/hashCode、可疑集合用法等 | `spotbugs-exclude.xml`（排除清单） |
| Checkstyle（源码风格） | 命名、导入、括号、空白、行长（≤150，存量分布上限口径） | `checkstyle.xml` |
| PMD（源码静态分析） | 未使用参数 / 私有方法 / 局部变量 / 私有字段 | `pmd-ruleset.xml` |
| CPD（重复代码） | 重复片段（≥45 tokens，约 5-8 行语句级；平行 DTO/VO 字段样板已抽基类收敛，平行 Runner/Service 家族样板属架构固有模式不触发） | 阈值在根 pom 的 cpd-check 执行配置 |
| Checkstyle MethodLength | 方法长度上限 100 行（核心 pipeline 编排方法 60-100 行属项目结构特征） | `checkstyle.xml` |

## 触发节点

- **构建门禁**：`mvn verify` 阶段自动执行 spotbugs:check（阈值 Low）+ checkstyle:check + pmd:check + pmd:cpd-check，有告警构建失败；
- **交付自检**：写完代码后手动执行 `mvn compile spotbugs:check checkstyle:check pmd:check pmd:cpd-check`，0 告警才交付；- 检查须从仓库根目录执行（规则文件经 `${maven.multiModuleProjectDirectory}` 解析）。

## 排除口径（评审记录）

- `EI_EXPOSE_REP / EI_EXPOSE_REP2`：Lombok 生成访问器与 Spring 容器注入字段对外暴露可变对象——项目通用模式，防御性拷贝与 DTO 风格冲突且无实际收益；
- `REC_CATCH_EXCEPTION`：任务链与解析器统一捕获 Exception 归一化错误码——捕获范围即调用边界；
- `CT_CONSTRUCTOR_THROW`（SpillBuffer）：构造期 SHA-256 算法探测失败即抛，fail-fast 防御；
- `IS2_INCONSISTENT_SYNC / UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR`（ParseTaskConsumer）：执行池字段仅启动期同步写入、运行期只读（配合 volatile 可见性）；
- `NP_*`（PdfBoxDocumentParser.buildLine / assemblePageElements、StructureAssemblerImpl.buildOrderRelations）：Hutool ObjectUtil 判空为项目统一风格，SpotBugs 无法识别其 null 语义产生误报，判空语义与原生 null 比较运行时等价。

## 等价性口径（相对 IDEA）

| IDEA 检查类别 | 等价引擎 | 残余差异 |
|---|---|---|
| 数据流 / 空指针 / 资源 | SpotBugs | IDEA 的字节码级路径更细，SpotBugs 按 bug 模式覆盖大部分 |
| 命名 / 导入 / 空白 | Checkstyle | IDEA 的实时排版提示无法完全复刻，规则集对齐存量风格 |
| 未使用参数/方法/字段 | PMD | 覆盖私有方法/参数/局部变量/私有字段（IDEA 同款口径） |
| 重复代码片段 | CPD | 阈值按语句级重复标定；字段样板类重复为设计固有，不触发 |
| 恒真条件/冗余判断 | 无等价引擎 | IDEA 数据流级分析，Maven 生态无同精度替代——交付自查项 |
| Spring 注入专项 | 无等价引擎 | 以"应用可启动 + 单测全绿"兜底 |

## 交付自查清单（机器覆盖不到的条款）

- [ ] 新增分支条件无"恒真/恒假"冗余判断（IDEA 数据流级，交付前人工核对）
- [ ] 新增 Serializable 实体带 serialVersionUID 并标注 @Serial（对齐 KbStrategyBinding 先例）
- [ ] 判空统一用 Hutool ObjectUtil（SpotBugs 误报走本文件排除清单，不改代码风格）
- [ ] 布尔方法命名与调用方向一致（无"恒取反"调用；IDEA 该建议无 Maven 等价规则）
- [ ] 新增子步骤/组装逻辑先查公共助手（StepLogHelper 等）再自行构造，避免低 token 重复片段
- [ ] 注释只写"做什么"（职责与行为）
