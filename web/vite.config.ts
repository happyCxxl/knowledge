import { fileURLToPath, URL } from 'node:url';

import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

// Vite 配置：Vue 插件 + @ 别名 + 后端接口开发代理（开发期经代理转发，免 CORS）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/auth': 'http://localhost:4388',
      '/knowledge-base': 'http://localhost:4388',
      '/strategy-versions': 'http://localhost:4388',
    },
  },
});
