/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { execSync } from 'node:child_process';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

function getGitCommit(): string {
  try {
    return execSync('git rev-parse --short HEAD', { encoding: 'utf-8' }).trim();
  } catch {
    return 'unknown';
  }
}

const backendProxyTarget = process.env.VITE_BACKEND_PROXY_TARGET ?? 'http://localhost:18080';

export default defineConfig({
  define: {
    __BUILD_TIME__: JSON.stringify(new Date().toISOString()),
    __GIT_COMMIT__: JSON.stringify(getGitCommit()),
    __BACKEND_URL__: JSON.stringify(backendProxyTarget),
  },
  plugins: [
    react(),
    VitePWA({
      disable: process.env.VITEST === 'true',
      registerType: 'prompt',
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'logo-full-light.svg', 'logo-full-dark.svg'],
      manifest: {
        name: 'Semantifyr Live',
        short_name: 'Semantifyr',
        description: 'Edit and verify Semantifyr models in your browser.',
        lang: 'en',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        theme_color: '#1e1e1e',
        background_color: '#1e1e1e',
        icons: [
          { src: 'pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'pwa-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: 'pwa.svg', sizes: 'any', type: 'image/svg+xml' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{css,html,ico,svg,png,json,webmanifest,woff,woff2,ttf}'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//, /^\/ws\//],
        cleanupOutdatedCaches: true,
        runtimeCaching: [
          {
            urlPattern: /\/assets\/.*\.js$/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'semantifyr-app-chunks',
              expiration: { maxEntries: 256, maxAgeSeconds: 60 * 60 * 24 * 30 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
      devOptions: { enabled: false },
    }),
  ],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': { target: backendProxyTarget, changeOrigin: true },
      '/ws/lsp': { target: backendProxyTarget, ws: true, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 8000,
  },
  worker: {
    format: 'es',
  },
  optimizeDeps: {
    // The codingame worker entry points must NOT go through Vite's pre-bundling step
    // -- Vite emits them as standalone worker bundles via the `new URL` references in
    // monaco-languageclient's defaultWorkerLoaders.
    exclude: [
      '@codingame/monaco-vscode-editor-api/esm/vs/editor/editor.worker',
      '@codingame/monaco-vscode-api/workers/extensionHost.worker',
      '@codingame/monaco-vscode-textmate-service-override/worker',
    ],
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/__tests__/setup.ts'],
    include: ['src/__tests__/**/*.test.{ts,tsx}'],
    // The LiveEditor module pulls in monaco/@codingame/* which is heavy and not testable
    // under jsdom. Tests that need a stand-in mock it explicitly.
    server: { deps: { inline: [/@testing-library/] } },
  },
});
