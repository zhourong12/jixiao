import path from 'path';
import { defineConfig } from '@lark-apaas/fullstack-vite-preset';

const serverPort = process.env.SERVER_PORT || '3000';

export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'client/src'),
    },
  },
  server: {
    proxy: {
      '/auth': {
        target: `http://localhost:${serverPort}`,
        changeOrigin: true,
      },
      '/api': {
        target: `http://localhost:${serverPort}`,
        changeOrigin: true,
      },
    },
  },
  // 生产构建在「rendering chunks」后会对每个 chunk 做 gzip 体积统计，大项目极慢且像卡死；关闭后显著缩短尾部耗时
  build: {
    reportCompressedSize: false,
  },
});
