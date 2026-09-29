import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 프론트는 백엔드(VITE_API_BASE_URL)를 직접 호출한다. 백엔드에 CORS 설정이 필요하다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
});
