/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { chromium, type Browser, type Locator, type Page } from 'playwright'
import { expect } from 'vitest'

const url = process.env.SEMANTIFYR_LIVE_URL
if (!url) {
  throw new Error('SEMANTIFYR_LIVE_URL is not set')
}
export const LIVE_URL = url.replace(/\/$/, '')

// the language servers start, SysML loads its standard library in the browser, and the engines verify; all of it may be slow on a shared runner
export const SLOW = 4 * 60 * 1000

const FINISHED = /status-(passed|failed|inconclusive|error)\b/

export async function launch(): Promise<Browser> {
  const executablePath = process.env.SCREEN_TEST_CHROMIUM
  return chromium.launch(executablePath ? { executablePath } : {})
}

const pageErrors: string[] = []

// every page a flow opens reports its uncaught errors, its workers' included, which fail the flow
export async function newPage(browser: Browser, viewport: { width: number, height: number }): Promise<Page> {
  const page = await browser.newPage({ viewport })
  page.on('pageerror', (error) => {
    pageErrors.push(`${page.url()}: ${error.stack ?? error.message}`)
  })
  return page
}

export function takePageErrors(): string[] {
  return pageErrors.splice(0)
}

export async function openPage(browser: Browser, path: string): Promise<Page> {
  const page = await newPage(browser, { width: 1440, height: 900 })
  await page.goto(`${LIVE_URL}${path}`)
  return page
}

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

// a token of the model's text, by the line that holds it
export function token(page: Page, line: string, text: string): Locator {
  return page.locator('.view-line', { hasText: line }).locator('span > span').filter({ hasText: text }).last()
}
