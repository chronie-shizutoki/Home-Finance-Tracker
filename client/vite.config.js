/**
 * Vite Client Config
 * @module vite.config
 * @description Client Vite Config for Home Finance Tracker Client
 */
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import path from 'path';

import replace from '@rollup/plugin-replace';
import pkg from './package.json' with { type: 'json' };

export default defineConfig({
  build: {
    target: 'esnext', // Target ESNext for modern CSS features (like nested selectors)
    // Use esbuild for CSS minification. The default lightningcss minifier strips
    // the standard `backdrop-filter` declaration when a `-webkit-backdrop-filter`
    // alias is present. Browsers that do not honour the -webkit- alias (e.g.
    // Firefox) then lose the effect entirely, which breaks paint order
    // (backdrop-filter creates a stacking context) and containing blocks for
    // absolutely-positioned children — observed as the invisible header back
    // button and overlapping dialog buttons in production builds.
    cssMinify: 'esbuild',
    rollupOptions: {
        input: {
          main: path.resolve(import.meta.dirname, 'index.html'),
          photo: path.resolve(import.meta.dirname, 'photo.html'),
        },
        output: {
          manualChunks: (id) => {
            if (!id.includes('node_modules')) return;
            if (id.includes('vue-router')) {
              return 'vue-router';
            }
            if (id.includes('pinia')) {
              return 'pinia';
            }
            if (id.includes('vue')) {
              return 'vue';
            }
            if (id.includes('axios') || id.includes('dayjs')) {
              return 'utils';
            }
            if (id.includes('xlsx')) {
              return 'excel';
            }
            if (id.includes('papaparse')) {
              return 'csv';
            }
            if (id.includes('marked')) {
              return 'markdown';
            }
            if (id.includes('highlight.js')) {
              return 'highlight';
            }
          }
        }
    },
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    }
  },

  base: '/', // Base URL for client-side routing
  assetsDir: 'assets', // Static assets directory (same as server public directory)
  server: {

    host: '0.0.0.0', // Listen on all network interfaces for local network access
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://0.0.0.0:3010', // Replace with actual backend local network IP
        secure: false,
        changeOrigin: true
      }
    },
    historyApiFallback: true
  },
  plugins: [
    replace({
      preventAssignment: true,
      __VERSION__: pkg.version,
      __BUILD_TIME__: new Date().toLocaleString()
    }),
    vue(),
    AutoImport({
      // Basic configuration for AutoImport plugin to work properly
      imports: ['vue'], // Auto-import Vue ref, reactive, etc.
      dts: 'auto-imports.d.ts'
    }),
    Components({
      // Basic configuration for Components plugin to work properly
      dts: 'components.d.ts',
      // Also auto-import the liquid-glass component library
      dirs: ['src/components', 'src/liquid-glass']
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
      vue: path.resolve(import.meta.dirname, 'node_modules/vue/dist/vue.esm-bundler.js'),
      // Force local memory cache implementation for CacheStore class
      CacheStore: path.resolve(import.meta.dirname, './src/utils/CacheStore.js')
    }
  }
});

