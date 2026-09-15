import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // サブドメイン単位でテナントを判定する設計（04_architecture.md §6.1）に合わせ、
    // <company_code>.localhost からの開発サーバーへのアクセスを許可する。
    allowedHosts: ['.localhost'],
  },
})
