/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import react from '@vitejs/plugin-react';
import { playwright } from '@vitest/browser-playwright';
import { defineConfig } from 'vitest/config';

// a local Chromium instead of Playwright's download, as on a developer machine
const executablePath = process.env.SCREEN_TEST_CHROMIUM;

export default defineConfig({
  define: {
    __VERSION__: JSON.stringify('screen'),
    __BUILD_TIME__: JSON.stringify('screen'),
    __GIT_COMMIT__: JSON.stringify('screen'),
    __BACKEND_URL__: JSON.stringify('http://live.example'),
  },
  plugins: [
    react(),
  ],
  optimizeDeps: {
    exclude: [
      '@codingame/monaco-vscode-editor-api/esm/vs/editor/editor.worker',
      '@codingame/monaco-vscode-api/workers/extensionHost.worker',
      '@codingame/monaco-vscode-textmate-service-override/worker',
    ],
  },
  worker: {
    format: 'es',
  },
  test: {
    include: ['src/__screens__/**/*.screen.test.tsx'],
    testTimeout: 120 * 1000,
    setupFiles: ['./src/__screens__/workers.ts'],
    browser: {
      enabled: true,
      headless: true,
      provider: playwright(executablePath ? { launchOptions: { executablePath } } : {}),
      instances: [
        {
          browser: 'chromium',
          viewport: { width: 1440, height: 900 },
        },
      ],
    },
  },
});
