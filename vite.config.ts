import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

const api = process.env.PORTFOLIO_API_URL ?? 'http://127.0.0.1:19186'
const target = new URL(api)
if (target.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(target.hostname)
  || target.username || target.password || target.pathname !== '/' || target.search || target.hash) {
  throw new Error('Development API proxy must use a plain loopback HTTP origin')
}
const proxy = { '/api': { target: api, changeOrigin: false } }
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: { proxy },
  preview: { proxy },
})
