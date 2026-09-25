import { fileURLToPath, URL } from 'node:url';
import type { IncomingMessage } from 'node:http';

import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

const BACKEND = 'http://localhost:4388';

/**
 * 浏览器页面导航（要 HTML）不走后端代理，交回 Vite 的 SPA 回退。
 *
 * <p>接口前缀与 SPA 路由会重叠（`/knowledge-base` 既是页面路由又是接口前缀），
 * 只靠路径无法区分，否则直接访问页面会被转发给后端、把接口的 JSON 当页面渲染。
 * 这里按 Accept 头区分：导航请求 → 返回路径跳过代理；接口请求（axios，Accept 为 json）→ 正常代理。
 *
 * @param req 代理收到的请求
 * @returns 需要跳过代理的路径；返回 undefined 表示继续代理
 */
function bypassNavigation(req: IncomingMessage): string | undefined {
  const accept = req.headers.accept ?? '';
  if (accept.includes('text/html')) {
    return req.url;
  }
  return undefined;
}

// Vite 配置：Vue 插件 + @ 别名 + 后端接口开发代理（开发期经代理转发，免 CORS）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/auth': { target: BACKEND, bypass: bypassNavigation },
      // /knowledge-base 与 SPA 路由同名，必须靠 bypass 区分导航与接口
      '/knowledge-base': { target: BACKEND, bypass: bypassNavigation },
      // /strategy-versions 目前仅接口使用，加 bypass 是为了口径统一、防未来重名
      '/strategy-versions': { target: BACKEND, bypass: bypassNavigation },
      // 用户接口：只代理 /user/ 下的子路径，避免把 SPA 路由 /user 本身也转发到后端
      '^/user/': { target: BACKEND, bypass: bypassNavigation },
    },
  },
});
