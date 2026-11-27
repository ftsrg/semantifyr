/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/__flows__/**/*.flow.test.ts'],
    testTimeout: 6 * 60 * 1000,
    hookTimeout: 60 * 1000,
    fileParallelism: false,
  },
});
