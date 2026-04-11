/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { execSync } from 'node:child_process';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

function getGitCommit(): string {
  try {
    return execSync('git rev-parse --short HEAD', { encoding: 'utf-8' }).trim();
  } catch {
    return 'unknown';
  }
}

// In production the backend serves both the SPA and the LSP gateway, so
// the SPA can use same-origin URLs for /api/* and /ws/lsp/{flavor}. During
// `npm run dev` we still want that behaviour without having to special-case URLs in
// the React code, so the dev server proxies those prefixes to the backend on
// localhost:18080. Override the target with VITE_BACKEND_PROXY_TARGET if you run the
// backend on a different host or port.
const backendProxyTarget = process.env.VITE_BACKEND_PROXY_TARGET ?? 'http://localhost:18080';

export default defineConfig({
  define: {
    __BUILD_TIME__: JSON.stringify(new Date().toISOString()),
    __GIT_COMMIT__: JSON.stringify(getGitCommit()),
    __BACKEND_URL__: JSON.stringify(backendProxyTarget),
  },
  plugins: [react()],
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
