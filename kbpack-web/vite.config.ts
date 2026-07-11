import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    allowedHosts: ['kb.localhost', 'kb.localtest.me'],
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
      '/health': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('/node_modules/')) return undefined;
          if (
            id.includes('/node_modules/react/') ||
            id.includes('/node_modules/react-dom/') ||
            id.includes('/node_modules/react-is/') ||
            id.includes('/node_modules/scheduler/')
          ) return 'vendor-react';
          if (id.includes('/node_modules/@tanstack/')) return 'vendor-query';
          if (id.includes('/node_modules/react-router')) return 'vendor-router';
          if (id.includes('/node_modules/axios/')) return 'vendor-network';
          if (
            id.includes('/node_modules/@babel/runtime/') ||
            id.includes('/node_modules/classnames/') ||
            id.includes('/node_modules/@emotion/hash/') ||
            id.includes('/node_modules/@emotion/unitless/') ||
            id.includes('/node_modules/stylis/') ||
            id.includes('/node_modules/@ant-design/fast-color/')
          ) return 'vendor-runtime';
          return undefined;
        },
      },
    },
  },
});
