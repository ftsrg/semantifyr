/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import react from '@vitejs/plugin-react';
import { playwright } from '@vitest/browser-playwright';
import { defineConfig, type Plugin } from 'vitest/config';
import type { BrowserCommand } from 'vitest/node';

import { LAUNCH_OPTIONS, TEST_BROWSER } from './src/testBrowser.ts';

const PWA_REGISTER = 'virtual:pwa-register/react';
const PWA_REGISTER_STUB = '\0pwa-register-stub';

// the screens run without the service worker plugin; an unresolved import fails the dependency scan, and the
// bundler then optimises dependencies in the middle of a cold run and reloads the tests
const serviceWorkerStub: Plugin = {
  name: 'service-worker-stub',
  enforce: 'pre',
  resolveId: (id) => {
    return id === PWA_REGISTER ? PWA_REGISTER_STUB : null;
  },
  load: (id) => {
    return id === PWA_REGISTER_STUB ? 'export function useRegisterSW() { return { needRefresh: [false, () => {}], offlineReady: [false, () => {}], updateServiceWorker: () => Promise.resolve() } }' : null;
  },
};

export type MouseAction = 'mousePressed' | 'mouseMoved' | 'mouseReleased';

// a pointer of the engine's own input, in the coordinates of the page that frames the test
const mouse: BrowserCommand<[type: MouseAction, x: number, y: number, clickCount: number]> = async (context, type, x, y, clickCount) => {
  await context.page.mouse.move(x, y);
  if (type === 'mousePressed') {
    await context.page.mouse.down({ clickCount });
  }
  if (type === 'mouseReleased') {
    await context.page.mouse.up({ clickCount });
  }
};

export default defineConfig({
  // out of node_modules, which Gradle fingerprints as the install's output: a run's results would make the next run stale
  cacheDir: 'build/vite/screen',
  define: {
    __VERSION__: JSON.stringify('screen'),
    __BUILD_TIME__: JSON.stringify('screen'),
    __GIT_COMMIT__: JSON.stringify('screen'),
    __BACKEND_URL__: JSON.stringify('http://live.example'),
  },
  plugins: [
    react(),
    serviceWorkerStub,
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
      provider: playwright({ launchOptions: LAUNCH_OPTIONS }),
      commands: {
        mouse,
      },
      instances: [
        {
          browser: TEST_BROWSER,
          viewport: { width: 1440, height: 900 },
        },
      ],
    },
  },
});
