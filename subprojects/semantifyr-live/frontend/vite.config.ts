/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Plugin, Rollup } from 'vite';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { examplePages, withHead } from './src/examplePages.ts';
import { LIVE_URL, PAGE_DESCRIPTION, PRODUCT_NAME, SHORT_NAME } from './src/texts.ts';

const backendProxyTarget = process.env.VITE_BACKEND_PROXY_TARGET ?? 'http://localhost:18080';

const CARD = `${LIVE_URL}/social-card.png`;

function liveTexts(): Plugin {
  return {
    name: 'live-texts',
    enforce: 'post',
    transformIndexHtml: {
      order: 'pre',
      handler: (html, context) => {
        const texts = html.replaceAll('%LIVE_NAME%', PRODUCT_NAME).replaceAll('%LIVE_CARD%', CARD);
        return context.server === undefined ? texts : withHead(texts, PRODUCT_NAME, PAGE_DESCRIPTION);
      },
    },
    generateBundle(_, bundle) {
      const index = bundle['index.html'] as Rollup.OutputAsset;
      const template = String(index.source);
      index.source = withHead(template, PRODUCT_NAME, PAGE_DESCRIPTION);
      for (const page of examplePages(template)) {
        this.emitFile({
          type: 'asset',
          fileName: page.fileName,
          source: page.source,
        });
      }
    },
  };
}

export default defineConfig({
  define: {
    __VERSION__: JSON.stringify(process.env.SEMANTIFYR_VERSION ?? 'unknown'),
    __GIT_COMMIT__: JSON.stringify(process.env.SEMANTIFYR_COMMIT ?? 'unknown'),
  },
  plugins: [
    react(),
    liveTexts(),
    VitePWA({
      disable: process.env.VITEST === 'true',
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'logo-full-light.svg', 'logo-full-dark.svg'],
      manifest: {
        name: PRODUCT_NAME,
        short_name: SHORT_NAME,
        description: PAGE_DESCRIPTION,
        lang: 'en',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        theme_color: '#1e1e1e',
        background_color: '#1e1e1e',
        icons: [
          { src: 'logo-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'logo-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'logo-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: 'logo.svg', sizes: 'any', type: 'image/svg+xml' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{css,html,ico,svg,png,json,webmanifest,woff,woff2,ttf}'],
        // an example's page is for a link's preview alone, and offline the precached index.html serves every address
        globIgnores: ['*/*/index.html'],
        // pages come from the network, so a returning browser starts the deployed build; the precached index.html is only the offline page
        navigateFallback: null,
        directoryIndex: null,
        cleanupOutdatedCaches: true,
        runtimeCaching: [
          {
            urlPattern: ({ request }) => request.mode === 'navigate',
            handler: 'NetworkOnly',
            options: { precacheFallback: { fallbackURL: 'index.html' } },
          },
          {
            urlPattern: /\/assets\/.*\.js$/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'semantifyr-app-chunks',
              expiration: { maxEntries: 256, maxAgeSeconds: 60 * 60 * 24 * 30 },
              cacheableResponse: { statuses: [200] },
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
      '/sysml-worker': { target: backendProxyTarget, changeOrigin: true },
    },
  },
  publicDir: 'build/imported/public',
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
