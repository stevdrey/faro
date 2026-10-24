import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  // Disabled in ordinary demos/builds until FARO-040. Never enables the backend by itself.
  define: { __FARO_SYNTHETIC_ASR__: JSON.stringify(process.env.VITE_FARO_SYNTHETIC_ASR === 'true') },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/actuator': 'http://127.0.0.1:8080',
    },
  },
})
