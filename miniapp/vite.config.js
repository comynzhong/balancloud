import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5175,
    // 手机与公网访问要经 frp 打到这台机器，必须监听所有网卡（默认只绑回环时穿透会连接被拒）
    host: '0.0.0.0',
    proxy: {
      // 与后台同源：页面和接口共用一个端口，浏览器侧不需要 CORS
      '/dev-api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/dev-api/, '')
      }
    }
  }
})
