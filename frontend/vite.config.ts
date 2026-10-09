import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // O navegador fala só com o Vite; o Vite repassa /api para o Spring.
    // Mesmo "domínio" para o navegador: não precisa de CORS.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
