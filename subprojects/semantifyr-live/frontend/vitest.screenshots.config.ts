/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['src/__shots__/**/*.shots.ts'],
    globalSetup: ['./src/__shots__/browser.ts', './src/__ui__/staticServer.ts'],
    testTimeout: 240 * 1000,
    hookTimeout: 60 * 1000,
    fileParallelism: false,
    expect: {
      poll: {
        timeout: 60 * 1000,
        interval: 100,
      },
    },
  },
})
