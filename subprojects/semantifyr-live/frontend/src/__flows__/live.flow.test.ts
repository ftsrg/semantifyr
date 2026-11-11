/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { LIVE_URL, SLOW, test, token, verifyAll } from './browser'

function results(page: Page) {
  return {
    cases: page.locator('.results .case-row'),
    verifyAll: page.locator('.results').getByRole('button', { name: 'Verify all', exact: true }),
    trace: page.locator('section.trace .case-trace'),
  }
}

describe('each language on the running image', () => {
  for (const address of [
    '/sysml/level-crossing',
    '/gamma/level-crossing',
    '/oxsts/basics',
  ]) {
    test(`verifies every case of ${address} and opens a trace`, async ({ flow }) => {
      const page = await flow.open(address)
      const { cases, verifyAll: button, trace } = results(page)

      await verifyAll(cases, button)

      await expect.poll(async () => {
        return trace.count()
      }, { timeout: 30_000 }).toBe(1)
    })
  }
})

describe('the SysML v2 language server of the fork, in the page', () => {
  test('hovers a name, completes one and goes to a definition in the standard library', async ({ flow }) => {
    const page = await flow.open('/sysml/level-crossing')
    await expect.poll(async () => {
      return results(page).cases.count()
    }, { timeout: SLOW }).toBeGreaterThan(0)

    await token(page, 'port sensors : ~Sensors;', 'Sensors').click()
    await page.keyboard.press('Control+k')
    await page.keyboard.press('Control+i')
    await expect.poll(async () => {
      return page.locator('.monaco-hover').filter({ visible: true }).innerText()
    }, { timeout: 30_000 }).toContain('Sensors')
    await page.keyboard.press('Escape')

    await page.keyboard.press('End')
    await page.keyboard.press('Enter')
    await page.keyboard.type('port probe : Sens')
    await page.keyboard.press('Control+Space')
    await expect.poll(async () => {
      return page.locator('.suggest-widget .monaco-list-row').allInnerTexts()
    }, { timeout: 30_000 }).toEqual(expect.arrayContaining([expect.stringContaining('Sensors')]))
    await page.keyboard.press('Escape')

    await token(page, 'private import ScalarValues::Integer;', 'Integer').click()
    await page.keyboard.press('F12')
    await expect.poll(async () => {
      return page.locator('.tabs [role="tab"][aria-selected="true"]').innerText()
    }, { timeout: 30_000 }).toContain('ScalarValues.kerml')
  })
})

describe('the verifying embed in a frame', () => {
  test('verifies and grows the frame with the trace it opens', async ({ flow }) => {
    const page = await flow.open('/api/health', { width: 900, height: 900 })
    await page.setContent(`
      <iframe src="${LIVE_URL}/gamma/level-crossing?view=embed" style="width: 760px; height: 600px; border: 0"></iframe>
      <script>
        window.heights = []
        window.addEventListener('message', (event) => {
          if (event.data && event.data.type === 'semantifyr-live:height') {
            window.heights.push(event.data.height)
          }
        })
      </script>
    `)
    const heights = async (): Promise<number[]> => {
      return page.evaluate(() => {
        return (window as unknown as { heights: number[] }).heights
      })
    }
    const frame = page.frameLocator('iframe')

    await verifyAll(frame.locator('.embed-case'), frame.getByRole('button', { name: 'Verify all', exact: true }))
    const withTrace = frame.locator('.embed-case.has-trace').first()
    if (await withTrace.getAttribute('aria-expanded') === 'true') {
      await withTrace.click()
    }
    await expect.poll(async () => {
      return frame.locator('.embed-trace').count()
    }, { timeout: 30_000 }).toBe(0)
    const before = (await heights()).at(-1) ?? 0
    await withTrace.click()

    await expect.poll(async () => {
      return (await heights()).at(-1) ?? 0
    }, { timeout: 30_000 }).toBeGreaterThan(before)
  })
})
