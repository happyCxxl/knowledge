/** @type {import('stylelint').Config} */
// noinspection JSUnusedGlobalSymbols -- 默认导出由 stylelint 按约定消费，非 JS 代码引用
export default {
  extends: ['stylelint-config-standard'],
  ignoreFiles: ['node_modules/**', 'dist/**', 'public/**'],
  rules: {
    // §6 样式规范：语义类名 kebab-case / 禁 ID 与类型选择器
    'selector-class-pattern': '^[a-z][a-z0-9]*(-[a-z0-9]+)*$',
    'selector-max-id': 0,
    'selector-max-type': 0,
    // 嵌套与长度约束（§4.6/§6.4；CSS 无嵌套时天然满足）
    'max-nesting-depth': 3,
    'selector-max-compound-selectors': 3,
    'selector-max-specificity': '0,3,0',
    // 缩写与零值（§6.3/§6.2）
    'length-zero-no-unit': true,
    'declaration-block-no-shorthand-property-overrides': true,
    // 每行一条声明（§6.2）
    'declaration-block-single-line-max-declarations': 1,
  },
  overrides: [
    {
      files: ['**/*.vue'],
      customSyntax: 'postcss-html',
    },
  ],
};
