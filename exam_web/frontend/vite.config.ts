/** Vite 构建配置文件 —— 开发服务器与代理设置 */

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],                    // 启用 Vue SFC 编译插件
  server: {
    port: 3001,                        // 前端开发服务器端口
    proxy: {
      '/api': 'http://localhost:8081'  // 将 /api 开头的请求代理到后端（8081端口）
    }
  }
})
