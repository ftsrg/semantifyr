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
      // written by the cloneGrammars Gradle task
      '@grammars': fileURLToPath(new URL('build/grammars', import.meta.url)),
      '@docusaurus/BrowserOnly': fileURLToPath(new URL('src/__tests__/stubs/BrowserOnly.tsx', import.meta.url)),
      '@theme-original/CodeBlock/Buttons': fileURLToPath(new URL('src/__tests__/stubs/Buttons.tsx', import.meta.url)),
      '@theme-original/CodeBlock': fileURLToPath(new URL('src/__tests__/stubs/CodeBlock.tsx', import.meta.url)),
      '@theme/CodeBlock': fileURLToPath(new URL('src/__tests__/stubs/CodeBlock.tsx', import.meta.url)),
    },
  },
  test: {
    include: [
      'src/**/*.test.{ts,tsx}',
    ],
  },
});
