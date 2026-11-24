import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backend = process.env.CRYPTO_API_URL || 'http://127.0.0.1:8080'
const proxy = {
  '/api': {
    target: backend,
    changeOrigin: true,
    configure(server) {
      server.on('proxyRes', response => {
        if (response.headers['content-type']?.includes('text/event-stream')) {
          response.headers['cache-control'] = 'no-cache'
          response.headers['x-accel-buffering'] = 'no'
        }
      })
    }
  }
}

export default defineConfig({
  plugins: [vue()],
  server: { host: '127.0.0.1', port: 3000, proxy },
  preview: { host: '127.0.0.1', proxy }
})
