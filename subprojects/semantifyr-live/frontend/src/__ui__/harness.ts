/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Browser, BrowserContext, Locator, Page } from 'playwright'
import { expect, inject, test as base } from 'vitest'

import { launchTestBrowser } from '../testBrowser'
import { keepEvidence, recordEvidence } from './evidence'
import { MockBackend } from './mock/backend'

export const LIVE_URL = inject('liveUrl')

export interface OpenOptions {
  width?: number
  height?: number
  touch?: boolean
  // runs in the page before its own scripts
  before?: () => void
}

export interface Ui {
  backend: MockBackend
  // opens an address of the live frontend; the first page fixes the context's viewport and touch input
  open: (address: string, options?: OpenOptions) => Promise<Page>
  // a page of another site at the live address, holding the given markup, for a frame of the live frontend
  host: (markup: string, options?: OpenOptions) => Promise<Page>
  context: () => BrowserContext
}

const DESKTOP = { width: 1440, height: 900 }

export const test = base.extend<{ ui: Ui, browser: Browser }>({
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
  ui: async ({ browser, task }, provide) => {
    const backend = new MockBackend()
    const pageErrors: string[] = []
    let context: BrowserContext | null = null
    const contextFor = async (options: OpenOptions): Promise<BrowserContext> => {
      if (context) {
        return context
      }
      context = await browser.newContext({
        viewport: { width: options.width ?? DESKTOP.width, height: options.height ?? DESKTOP.height },
        hasTouch: options.touch ?? false,
        isMobile: options.touch ?? false,
        serviceWorkers: 'block',
      })
      await recordEvidence(context)
      await backend.install(context, LIVE_URL)
      return context
    }
    const pageOf = async (options: OpenOptions): Promise<Page> => {
      const page = await (await contextFor(options)).newPage()
      page.on('pageerror', (error) => {
        pageErrors.push(`${page.url()}: ${error.stack ?? error.message}`)
      })
      if (options.before) {
        await page.addInitScript(options.before)
      }
      return page
    }
    await provide({
      backend,
      open: async (address, options = {}) => {
        const page = await pageOf(options)
        await page.goto(`${LIVE_URL}${address}`)
        return page
      },
      host: async (markup, options = {}) => {
        const page = await pageOf(options)
        await page.route(`${LIVE_URL}/embedding-page`, async (route) => {
          await route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body style="margin: 0">${markup}</body></html>` })
        })
        await page.goto(`${LIVE_URL}/embedding-page`)
        return page
      },
      context: () => {
        if (!context) {
          throw new Error('no page is open')
        }
        return context
      },
    })
    const problems = [
      ...backend.problems.map((problem) => {
        return `mock backend: ${problem}`
      }),
      ...pageErrors.map((error) => {
        return `uncaught in the page: ${error}`
      }),
    ]
    const opened = context as BrowserContext | null
    if (opened) {
      await keepEvidence(opened, 'ui', `${task.file.name.replace(/^.*\//, '')} ${task.name}`, task.result?.state === 'fail' || problems.length > 0)
      await opened.close()
    }
    expect(problems, 'the mock backend answered everything and nothing was uncaught in the page').toEqual([])
  },
})

export function results(page: Page) {
  return {
    column: page.locator('.results'),
    rows: page.locator('.results .case-row'),
    row: (name: string | RegExp): Locator => {
      return page.locator('.results .case-row').filter({ has: page.locator('.case-name', { hasText: name }) })
    },
    verifyAll: page.locator('.results').getByRole('button', { name: 'Verify all', exact: true }),
    cancel: page.locator('.results .button-cancel'),
    initialization: page.locator('.results .initialization'),
    trace: page.locator('section.trace'),
    status: page.locator('.statusbar'),
  }
}

export function editor(page: Page) {
  return {
    lines: page.locator('section.editor .view-lines').first(),
    marks: page.locator('section.editor .verdict-glyph'),
    tabs: page.locator('.tabs [role="tab"]'),
    selectedTab: page.locator('.tabs [role="tab"][aria-selected="true"]'),
  }
}

// the classes of a row or mark that name its state
export async function statesOf(locator: Locator, prefix: string): Promise<string[]> {
  return locator.evaluateAll((elements: Element[], prefixOfState: string) => {
    return elements.map((element) => {
      return [...element.classList].filter((name) => {
        return name.startsWith(prefixOfState) && name !== `${prefixOfState}outdated`
      }).map((name) => {
        return name.slice(prefixOfState.length)
      }).join(' ')
    })
  }, prefix)
}

// the cases are listed once initialization is over, which in SysML waits for the standard library the worker loads
export async function casesListed(page: Page): Promise<void> {
  const { rows, initialization } = results(page)
  await expect.poll(async () => {
    return await initialization.count() === 0 && await rows.count() > 0
  }, { timeout: 150_000 }).toBe(true)
}

// collects every text the status line shows from the page's start, which a test reads with seenStatusTexts
export function watchStatusTexts(): void {
  const seen = new Set<string>()
  Object.assign(window, { statusTexts: seen })
  new MutationObserver(() => {
    const text = document.querySelector<HTMLElement>('[data-testid=status-message]')?.innerText
    if (text !== undefined) {
      seen.add(text)
    }
  }).observe(document, { subtree: true, childList: true, characterData: true })
}

export async function seenStatusTexts(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    return [...(window as unknown as { statusTexts: Set<string> }).statusTexts]
  })
}
