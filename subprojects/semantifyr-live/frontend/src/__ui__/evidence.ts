/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { mkdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { BrowserContext } from 'playwright'

import { TEST_BROWSER } from '../testBrowser'

const EVIDENCE = resolve(import.meta.dirname, '../../build/evidence')

function folderName(name: string): string {
  return name.replace(/[^a-zA-Z0-9-]+/g, '-').replace(/^-|-$/g, '').slice(0, 120)
}

export async function recordEvidence(context: BrowserContext): Promise<void> {
  await context.tracing.start({ screenshots: true, snapshots: true })
}

// a failed test leaves its trace and a screenshot of each page under build/evidence/<engine>/<level>/<test>, a passing one nothing
export async function keepEvidence(context: BrowserContext, level: string, test: string, failed: boolean): Promise<void> {
  if (!failed) {
    await context.tracing.stop()
    return
  }
  const folder = join(EVIDENCE, TEST_BROWSER, level, folderName(test))
  mkdirSync(folder, { recursive: true })
  let index = 0
  for (const page of context.pages()) {
    await page.screenshot({ path: join(folder, `page-${index++}.png`) }).catch(() => {})
  }
  await context.tracing.stop({ path: join(folder, 'trace.zip') })
}
