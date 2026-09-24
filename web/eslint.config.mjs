import js from '@eslint/js';
import pluginVue from 'eslint-plugin-vue';
import tseslint from 'typescript-eslint';

export default [
  {
    ignores: ['node_modules/**', 'dist/**', 'public/**', '.husky/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/strongly-recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
  },
  {
    files: ['**/*.{js,mjs,cjs}'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: {
        console: 'readonly',
        window: 'readonly',
        document: 'readonly',
        navigator: 'readonly',
        localStorage: 'readonly',
        sessionStorage: 'readonly',
        process: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
      },
    },
    rules: {
      // 语言底线（§4）：const 优先 / 禁 var / 大括号必加 / 严格相等 / 字面量
      'no-var': 'error',
      'prefer-const': 'error',
      curly: ['error', 'all'],
      eqeqeq: ['error', 'always'],
      'no-new-object': 'error',
      'no-array-constructor': 'error',
      quotes: ['error', 'single', { avoidEscape: true }],
      semi: ['error', 'always'],
      // 调试日志不得提交（§4.9，【应该】级先告警）
      'no-console': 'warn',
    },
  },
  {
    files: ['src/**/*.{ts,vue}'],
    rules: {
      // §2.3/§8：类型化接口层，禁止 any
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': 'error',
      // 接口层收口（§2.3）：HTTP 库只允许出现在 src/api 内
      'no-restricted-imports': [
        'error',
        {
          paths: [{ name: 'axios', message: 'HTTP 调用只允许写在 src/api/ 目录（见规范 §2.3）' }],
        },
      ],
      // §5.12：模板组件名 PascalCase + 自闭合
      'vue/component-name-in-template-casing': ['error', 'PascalCase'],
      'vue/html-self-closing': [
        'error',
        { html: { void: 'always', normal: 'never', component: 'always' } },
      ],
      // §5.12：元素 attribute 顺序（【应该】级告警）
      'vue/attributes-order': 'warn',
    },
  },
  {
    files: ['src/api/**/*.{ts,vue}'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
  {
    files: ['scripts/**/*.mjs'],
    languageOptions: {
      globals: {
        console: 'readonly',
        process: 'readonly',
      },
    },
    rules: {
      // 校验/工具脚本通过控制台输出结果，不受"业务代码禁 console"约束
      'no-console': 'off',
    },
  },
];
