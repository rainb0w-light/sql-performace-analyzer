import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          // 将 node_modules 中的依赖分离
          if (id.includes('node_modules')) {
            // Element Plus 及其图标库单独打包
            if (id.includes('element-plus')) {
              return 'element-plus'
            }
            // Vue 相关库打包在一起
            if (id.includes('vue') || id.includes('vue-router')) {
              return 'vue-vendor'
            }
            // 其他第三方库
            return 'vendor'
          }
        }
      }
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:18881',
        changeOrigin: true
      }
    }
  }
})






