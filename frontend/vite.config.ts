import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies API + actuator to the Spring Boot backend on :8080.
export default defineConfig({
  plugins: [react()],
  build: {
    // Two independent SPAs from one build: the wallet (index.html) and the
    // admin panel (admin.html). Relative entries resolve against the project
    // root, so no node:url plumbing is needed.
    rollupOptions: {
      input: {
        main: 'index.html',
        admin: 'admin.html',
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
