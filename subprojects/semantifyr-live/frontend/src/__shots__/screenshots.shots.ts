/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { mkdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { Locator, Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, results, statesOf, test } from '../__ui__/harness'

// the pages show these, taken from live's own build against the interface tests' mock backend
const SCREENSHOTS = resolve(import.meta.dirname, '../../build/screenshots')

const THEMES = [
  'light',
  'dark',
] as const

// every case has its verdict and its witness is validated, so nothing in the picture is still moving
async function verdictsShown(page: Page, rows: Locator): Promise<void> {
  await expect.poll(async () => {
    const states = await statesOf(rows, 'status-')
    return states.length > 0 && states.every((state) => {
      return state === 'passed' || state === 'failed'
    })
  }).toBe(true)
  await expect.poll(async () => {
    return page.locator('.validation-mark.is-validating, .validation-mark.is-validation-queued').count()
  }).toBe(0)
  await page.evaluate(async () => {
    await document.fonts.ready
  })
}

async function shoot(page: Page, name: string): Promise<void> {
  mkdirSync(SCREENSHOTS, { recursive: true })
  await page.mouse.move(0, 0)
  await page.screenshot({ path: join(SCREENSHOTS, `${name}.png`), animations: 'disabled', caret: 'hide' })
}

describe('the screenshots', () => {
  for (const theme of THEMES) {
    test(`live on the SysML v2 level crossing, verified with its trace open, ${theme}`, async ({ ui }) => {
      const page = await ui.open('/sysml/level-crossing', { width: 1280, height: 800 })
      await page.emulateMedia({ colorScheme: theme })
      await casesListed(page)
      await results(page).verifyAll.click()
      await verdictsShown(page, results(page).rows)
      await expect.poll(async () => {
        return page.locator('section.trace .case-trace .trace-frame .trace-editor:not(.is-loading)').count()
      }).toBe(1)

      await shoot(page, `live-${theme}`)
    })

    test(`the embedded editor on the intersection lesson, verified, ${theme}`, async ({ ui }) => {
      const page = await ui.open(`/oxsts/intersection?view=embed&theme=${theme}`, { width: 800, height: 720 })
      const cases = page.locator('.embed-case')
      await expect.poll(async () => {
        return cases.count()
      }).toBeGreaterThan(0)
      await page.getByRole('button', { name: 'Verify all', exact: true }).click()
      await verdictsShown(page, cases)

      await shoot(page, `embed-${theme}`)
    })
  }
})
