/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { defineConfig } from 'vitest/config';

export default defineConfig({
  // out of node_modules, which Gradle fingerprints as the install's output: a run's results would make the next run stale
  cacheDir: 'build/vite/smoke',
  test: {
    include: ['src/__smoke__/**/*.test.ts'],
    testTimeout: 5 * 60 * 1000,
  },
});
