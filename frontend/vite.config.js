import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, strictPort: true, proxy: {
    '/api/auth': { target: process.env.IDENTITY_API_URL || 'http://127.0.0.1:8087' },
    '/api/ops/content': { target: 'http://127.0.0.1:8086' },
    '/api/ops/feedback': { target: 'http://127.0.0.1:8086' },
    '/api/ops/reports': { target: 'http://127.0.0.1:8089' },
    '/api/ops/inspections': { target: 'http://127.0.0.1:8088' },
    '/api/inspections': { target: 'http://127.0.0.1:8088' },
    '/api/attachments': { target: 'http://127.0.0.1:8088' },
    '/api/reports': { target: 'http://127.0.0.1:8089' },
    '/api/ops': { target: process.env.IDENTITY_API_URL || 'http://127.0.0.1:8087' },
    '/api': { target: 'http://127.0.0.1:8086' }
  } }
})
