# 前端检查

`knowledge-web` 的静态检查与规范校验。实现分两处：**本目录放自研脚本，`web/` 放第三方工具的配置**。

## 组成

| 检查          | 实现位置                                       | 由谁执行                                                  |
|---------------|------------------------------------------------|-----------------------------------------------------------|
| 格式          | `web/.prettierrc.json` + `web/.prettierignore` | `pnpm lint:format`；提交钩子对暂存文件 `prettier --write` |
| JS / Vue 代码 | `web/eslint.config.mjs`                        | `pnpm lint:js`；提交钩子对暂存文件 `eslint --fix`         |
| 样式          | `web/stylelint.config.mjs`                     | `pnpm lint:css`；提交钩子对暂存文件 `stylelint --fix`     |
| 文件命名      | `web/.ls-lint.yml`                             | `pnpm lint:files`；推送钩子全树执行                       |
| 接口命名词表  | 本目录 `check-naming.mjs`                      | `pnpm lint:names`；推送钩子全树执行                       |
| 工程规范合规  | 本目录 `check-spec.mjs`                        | `pnpm lint:spec`；推送钩子全树执行                        |
| 类型          | `web/tsconfig.json`                            | `pnpm lint:types`（`vue-tsc -b`）；推送钩子全树执行       |

七步的入口是 `web/package.json` 的 `lint` 脚本；**推送钩子（`.husky/pre-push` 前端组）跑的就是 `pnpm lint`**——全量、只报不改。**提交钩子（`.husky/pre-commit` 前端组）只做就地修复**：对暂存文件跑 lint-staged（prettier / eslint / stylelint 的 fix 结果回写索引），让推送时的全量检查能过。两段的分工是因为全量检查放在提交路径上太慢。

## 为什么第三方工具的配置留在 `web/`

`pnpm lint` 的七步都以 `web/` 为工作目录执行，而第三方工具**按位置自动发现配置**；其中两处实测确认搬不动：

- **`eslint.config.mjs` 的 ESM 导入按文件自身位置解析**。搬到本目录后会直接失败：
  `ERR_MODULE_NOT_FOUND: Cannot find package '@eslint/js' imported from .../tools/frontend/eslint.config.mjs`
  ——依赖只装在 `web/node_modules`。要搬就得在本目录再装一份依赖（体积翻倍），或做 `node_modules` 链接（git 不跟踪，clone 后需手工重建）。
- **`.prettierignore` 的模式按该文件自身位置解析**。搬到本目录后 `docs/**/*.md` 会改指 `tools/frontend/docs/`，原本被排除的文档重新参与检查。改写成 `**/docs/**/*.md` 这类位置无关模式可以绕开，但会牺牲可读性。

此外，配置留在 `web/` 也便于编辑器与 IDE 的前端插件就近发现它们。

`check-naming.mjs` 与 `check-spec.mjs` 是自研脚本、没有位置约定，由 `web/package.json` 以相对路径 `node ../tools/frontend/xxx.mjs` 调用，故放在本目录归拢。代价是 `web/` 对 `tools/` 有一个稳定的相对路径耦合。

## 维护须知

- **`web/` 下新增点目录时，要往 `web/.ls-lint.yml` 的 `ignore` 补一条**：ls-lint 会把点目录本身判为不符合 kebab-case，并进入目录内检查文件（`.git` / `.idea` / `.vscode` 就是因此列入的）。
- 改规则时：内容检查改 `web/` 下的配置，命名词表与规范合规改本目录的脚本。两者都在钩子覆盖范围内（就地修复在提交前，全量校验在推送前）。
