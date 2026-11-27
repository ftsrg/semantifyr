/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  cacheDir: 'build/vite/test',
  // the site's tsconfig leaves JSX to Docusaurus
  oxc: {
    jsx: {
      runtime: 'automatic',
    },
  },
  resolve: {
    alias: {
      '@site': fileURLToPath(new URL('.', import.meta.url)),
      '@theme/CodeBlock': fileURLToPath(new URL('src/__tests__/stubs/CodeBlock.tsx', import.meta.url)),
    },
  },
  test: {
    include: [
      'src/**/*.test.{ts,tsx}',
    ],
  },
});
