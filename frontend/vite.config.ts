import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// WHY: 画面から /api を同じオリジンで呼べるよう、開発サーバーがバックエンド（8081）へ中継する。CORS の設定が要らない。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8081' },
  },
})
