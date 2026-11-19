import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Spring do desenvolvimento (8080). O modo casa (scripts/aurora-casa.ps1) aponta para o dele (8090).
const apiUrl = process.env.AURORA_API_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // O navegador fala só com o Vite; o Vite repassa /api para o Spring.
    // Mesmo "domínio" para o navegador: não precisa de CORS.
    proxy: {
      '/api': apiUrl,
    },
  },
  // "vite preview" serve o frontend compilado (modo casa) com o mesmo repasse de /api.
  preview: {
    proxy: {
      '/api': apiUrl,
    },
  },
})
