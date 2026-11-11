/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Browser, BrowserContext, Locator, Page } from 'playwright'
import { expect, test as base } from 'vitest'

import { launchTestBrowser } from '../testBrowser'
import { keepEvidence, recordEvidence } from '../__ui__/evidence'

const url = process.env.SEMANTIFYR_LIVE_URL
if (!url) {
  throw new Error('SEMANTIFYR_LIVE_URL is not set')
}
export const LIVE_URL = url.replace(/\/$/, '')

// the language servers start, SysML loads its standard library in the browser, and the engines verify; all of it may be slow on a shared runner
export const SLOW = 4 * 60 * 1000

const FINISHED = /status-(passed|failed|inconclusive|error)\b/

export interface Flow {
  open: (address: string, viewport?: { width: number, height: number }) => Promise<Page>
}

// each flow records its trace in a context of its own, kept with a screenshot when the flow fails; an uncaught error in a page fails the flow
export const test = base.extend<{ flow: Flow, browser: Browser }>({
  browser: [
    // vitest reads the fixtures a fixture needs from the pattern of its first parameter, and this one needs none
    // eslint-disable-next-line no-empty-pattern
    async ({}, provide) => {
      const browser = await launchTestBrowser()
      await provide(browser)
      await browser.close()
    },
    { scope: 'worker' },
  ],
  flow: async ({ browser, task }, provide) => {
    const pageErrors: string[] = []
    const contexts: BrowserContext[] = []
    await provide({
      open: async (address, viewport = { width: 1440, height: 900 }) => {
        const context = await browser.newContext({ viewport, serviceWorkers: 'block' })
        await recordEvidence(context)
        contexts.push(context)
        const page = await context.newPage()
        page.on('pageerror', (error) => {
          pageErrors.push(`${page.url()}: ${error.stack ?? error.message}`)
        })
        await page.goto(`${LIVE_URL}${address}`)
        return page
      },
    })
    for (const context of contexts) {
      await keepEvidence(context, 'flows', task.name, task.result?.state === 'fail' || pageErrors.length > 0)
      await context.close()
    }
    expect(pageErrors, 'uncaught errors in the page').toEqual([])
  },
})

// every case of the model reaches a verdict, whichever engines the server has
export async function verifyAll(cases: Locator, verifyButton: Locator): Promise<void> {
  await expect.poll(async () => {
    return cases.count()
  }, { timeout: SLOW }).toBeGreaterThan(0)
  const classesOf = async (): Promise<string[]> => {
    return cases.evaluateAll((rows: Element[]) => {
      return rows.map((row) => {
        return row.getAttribute('class') ?? ''
      })
    })
  }
  await verifyButton.click()
  await expect.poll(async () => {
    return (await classesOf()).every((className) => {
      return FINISHED.test(className)
    })
  }, { timeout: SLOW, interval: 1000 }).toBe(true)
  expect((await classesOf()).map((className) => {
    return FINISHED.exec(className)?.[1]
  }).filter((verdict) => {
    return verdict !== 'passed' && verdict !== 'failed'
  })).toEqual([])
}

// the span a token sits in depends on how far the highlighting has split the line, so the click goes to the name's own text range
export async function clickToken(page: Page, line: string, text: string): Promise<void> {
  const center = await page.locator('.view-line', { hasText: line }).first().evaluate((element, name) => {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
    const nodes: Text[] = []
    while (walker.nextNode()) {
      nodes.push(walker.currentNode as Text)
    }
    const content = nodes.map((node) => {
      return node.data
    }).join('').replaceAll('\u00a0', ' ')
    let offset = content.lastIndexOf(name)
    const range = document.createRange()
    for (const node of nodes) {
      if (offset < node.length) {
        range.setStart(node, offset)
        range.setEnd(node, offset + name.length)
        break
      }
      offset -= node.length
    }
    const box = range.getBoundingClientRect()
    return { x: box.x + box.width / 2, y: box.y + box.height / 2 }
  }, text)
  await page.mouse.click(center.x, center.y)
}
