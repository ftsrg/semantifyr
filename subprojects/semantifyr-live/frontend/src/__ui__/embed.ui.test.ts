/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { LIVE_URL, statesOf, test } from './harness'

function framing(address: string, style: string): string {
  return `
    <iframe src="${LIVE_URL}${address}" style="border: 0; ${style}"></iframe>
    <script>
      window.heights = []
      window.addEventListener('message', (event) => {
        if (event.data && event.data.type === 'semantifyr-live:height') {
          window.heights.push(event.data.height)
        }
      })
    </script>
  `
}

async function heightsOf(page: Page): Promise<number[]> {
  return page.evaluate(() => {
    return (window as unknown as { heights: number[] }).heights
  })
}

describe('the verifying embed', () => {
  test('verifies its cases, opens the trace of a case below it and tells the page the height it grows to', async ({ ui }) => {
    const page = await ui.host(framing('/oxsts/level-crossing?library=gamma&view=embed', 'width: 760px; height: 600px'))
    const frame = page.frameLocator('iframe')
    const cases = frame.locator('.embed-case')
    await expect.poll(async () => {
      return cases.count()
    }).toBe(2)
    expect((await ui.backend.openSession()).query.get('kind')).toBe('embed')

    await frame.getByRole('button', { name: 'Verify all', exact: true }).click()
    await expect.poll(async () => {
      return statesOf(cases, 'status-')
    }).toEqual(['failed', 'passed'])
    const opened = frame.locator('.embed-case.is-open')
    if (await opened.count() > 0) {
      await opened.click()
    }
    await expect.poll(async () => {
      return frame.locator('.embed-trace').count()
    }).toBe(0)
    await expect.poll(async () => {
      return (await heightsOf(page)).length
    }).toBeGreaterThan(0)
    const before = (await heightsOf(page)).at(-1) ?? 0
    await frame.locator('.embed-case.has-trace').first().click()

    await expect.poll(async () => {
      return frame.locator('.embed-trace .case-trace').count()
    }).toBe(1)
    await expect.poll(async () => {
      return (await heightsOf(page)).at(-1) ?? 0
    }).toBeGreaterThan(before)
    expect(await frame.locator('.settings-popover, [aria-label="Portfolios and validation"], .tabs').count()).toBe(0)
  })

  test('as edit-only is the editor alone, on a session of its own kind', async ({ ui }) => {
    const page = await ui.host(framing('/oxsts/intersection?view=edit-only', 'width: 760px; height: 600px'))
    const frame = page.frameLocator('iframe')

    await expect.poll(async () => {
      return frame.locator('section.editor .view-lines, .embed-editor .view-lines').first().innerText()
    }).toContain('package')
    expect((await ui.backend.openSession()).query.get('kind')).toBe('edit-only')
    expect(await frame.locator('.embed-strip').count()).toBe(0)
  })

  test('with frame=fixed reports no height, and its divider moves the results', async ({ ui }) => {
    const page = await ui.host(framing('/oxsts/level-crossing?library=gamma&view=embed&frame=fixed', 'width: 1100px; height: 500px'))
    const frame = page.frameLocator('iframe')
    await expect.poll(async () => {
      return frame.locator('.embed-case').count()
    }).toBe(2)
    const strip = frame.locator('.embed-strip')
    const width = (await strip.boundingBox())?.width ?? 0
    const divider = frame.getByRole('separator', { name: 'Width of the results' })
    const box = (await divider.boundingBox())!
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    await page.mouse.down()
    await page.mouse.move(box.x - 120, box.y + box.height / 2, { steps: 5 })
    await page.mouse.up()

    await expect.poll(async () => {
      return Math.round(((await strip.boundingBox())?.width ?? 0) - width)
    }).toBeGreaterThan(100)
    await page.waitForTimeout(500)
    expect(await heightsOf(page)).toEqual([])
  })

  for (const theme of ['dark', 'light'] as const) {
    test(`shows the ${theme} theme its address names, whatever the system prefers, and stores none`, async ({ ui }) => {
      const page = await ui.host(framing(`/oxsts/intersection?view=embed&theme=${theme}`, 'width: 760px; height: 600px'))
      await page.emulateMedia({ colorScheme: theme === 'dark' ? 'light' : 'dark' })
      const frame = page.frames().find((candidate) => {
        return candidate.url().includes('view=embed')
      })

      await expect.poll(async () => {
        return frame?.evaluate(() => {
          return document.documentElement.dataset.theme
        })
      }).toBe(theme)
      expect(await frame?.evaluate(() => {
        return window.localStorage.getItem('semantifyr-live-color-mode')
      })).toBeNull()
    })
  }
})
