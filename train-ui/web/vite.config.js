import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 手机浏览器 H5：开发代理 /api → 网关 8000
export default defineConfig(({ mode }) => {
  // 敏感字段密钥,缺失或长度不符直接终止构建
  const key = loadEnv(mode, process.cwd(), '').VITE_AES_KEY
  if (!key || key.length !== 16) {
    throw new Error('未配置 VITE_AES_KEY,与后端 NTRAIN_AES_KEY 保持一致')
  }
  return {
    plugins: [vue()],
    server: {
      port: 5173,
      host: '0.0.0.0',
      proxy: {
        '/api': {
          target: 'http://localhost:8000',
          changeOrigin: true
        }
      }
    }
  }
})
