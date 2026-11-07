import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// The dashboard calls the API same-origin at /api/* and /actuator/*. In dev, Vite
// proxies those to the backend (VITE_API_TARGET, default localhost:8080). In prod
// the Netlify reverse proxy serves the SPA and forwards /api to the API host, so
// the app code never hard-codes an API origin (and the refresh cookie stays
// first-party). The browser Origin stays http://localhost:5173 in dev, which the
// backend's auth.csrf.allowed-origins allowlist accepts.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_API_TARGET || 'http://localhost:8080'
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target, changeOrigin: false },
        '/actuator': { target, changeOrigin: false },
      },
    },
  }
})
