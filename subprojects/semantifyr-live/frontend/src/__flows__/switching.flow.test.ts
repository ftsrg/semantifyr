/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Browser, Page } from 'playwright'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { launch, LIVE_URL, newPage, SLOW, takePageErrors } from './browser'

// Goal: however the visitor switches languages, flavours and examples, quickly or slowly, with runs and read-only tabs in between,
// every switch ends with the cases listed or a reason shown, and the margin carries exactly the marks of the shown document's cases, one state each

let browser: Browser

beforeAll(async () => {
  browser = await launch()
})

// a page closed with its session open reports the language client's failed disposal, an upstream fault, so the pages close with the browser
afterAll(async () => {
  await browser.close()
})

afterEach(() => {
  expect(takePageErrors(), 'uncaught errors in the page').toEqual([])
})

type Step =
  | { kind: 'language', name: string }
  | { kind: 'flavour', name: string }
  | { kind: 'example', name: string }
  | { kind: 'verify' }
  | { kind: 'library' }
  | { kind: 'model' }
  | { kind: 'back' }

const LANGUAGES = [
  'SysML v2',
  'Gamma',
  'OXSTS',
]

const FLAVOURS = [
  'Plain',
  'With the Gamma library',
  'With the SysML v2 library',
]

// the first examples of each language and flavour, which the menu lists by these labels
const EXAMPLES = [
  'Level crossing',
  'Tutorial: Basics',
  'Tutorial: Intermediate',
  'Leader-worker',
  'Door access',
  'Blank model',
]

const WAITS = [
  0,
  150,
  600,
  2000,
]

// the examples whose every case the tall page shows at once
const LONG_EXAMPLES = new Set([
  'advanced',
  'crossroads',
  'spacecraft',
  'compressedspacecraft',
  'orion-protocol',
])

// a fixed sequence, so a failure is replayed as it happened
function stepsOf(count: number, seed: number): { step: Step, wait: number }[] {
  let state = seed
  const next = (bound: number): number => {
    state = (state * 1103515245 + 12345) % 2147483648
    return Math.floor((state / 2147483648) * bound)
  }
  const pick = <T>(list: readonly T[]): T => {
    return list[next(list.length)]!
  }
  return Array.from({ length: count }, () => {
    const kind = pick(['language', 'language', 'language', 'flavour', 'example', 'example', 'verify', 'library', 'model', 'back'] as const)
    const wait = pick(WAITS)
    switch (kind) {
      case 'language':
        return { step: { kind, name: pick(LANGUAGES) }, wait }
      case 'flavour':
        return { step: { kind, name: pick(FLAVOURS) }, wait }
      case 'example':
        return { step: { kind, name: pick(EXAMPLES) }, wait }
      default:
        return { step: { kind }, wait }
    }
  })
}

async function take(page: Page, step: Step): Promise<void> {
  switch (step.kind) {
    case 'language':
      await page.locator('.language-switch [role=group] button', { hasText: step.name }).first().click()
      return
    case 'flavour': {
      const flavour = page.locator('.flavour-button')
      if (await flavour.count() === 0) {
        return
      }
      await flavour.click()
      await page.getByRole('menuitem', { name: new RegExp(step.name) }).click()
      return
    }
    case 'example': {
      await page.locator('.example-button').click()
      const item = page.getByRole('menuitem', { name: new RegExp(`^${step.name}`) })
      if (await item.count() === 0) {
        await page.getByRole('menuitem').first().click()
        return
      }
      await item.first().click()
      return
    }
    case 'verify': {
      const button = page.locator('.results').getByRole('button', { name: 'Verify all', exact: true })
      if (await button.count() > 0 && await button.isEnabled()) {
        await button.click()
      }
      return
    }
    case 'library':
      await page.getByRole('button', { name: 'Read-only documents' }).click()
      await page.locator('.documents-group:not([aria-label="Selected case"]):not([aria-label="Model"]) .menu-item').first().click()
      return
    case 'model':
      await page.locator('.tabs [role="tab"]').first().click()
      return
    case 'back':
      await page.goBack()
      // Back past the first address leaves the application
      if (!page.url().startsWith(LIVE_URL)) {
        await page.goForward()
      }
      return
  }
}

interface Shown {
  path: string
  initializing: string | null
  rows: string[]
  marks: string[]
  doubled: string[]
}

async function shown(page: Page): Promise<Shown> {
  return page.evaluate(() => {
    const stateOf = (classes: string[], prefix: string): string[] => {
      return classes.filter((name) => {
        return name.startsWith(prefix) && name !== `${prefix}outdated`
      }).map((name) => {
        return name.slice(prefix.length)
      })
    }
    const marks = [...document.querySelectorAll('section.editor .verdict-glyph')].map((mark) => {
      return stateOf([...mark.classList], 'verdict-glyph-')
    })
    const initialization = document.querySelector<HTMLElement>('.results .initialization')
    return {
      path: `${location.pathname}${location.search}`,
      initializing: initialization ? initialization.innerText : null,
      rows: [...document.querySelectorAll('.results .case-row')].map((row) => {
        return stateOf([...row.classList], 'status-').join(' ')
      }).sort(),
      marks: marks.map((states) => {
        return states.join(' ')
      }).sort(),
      doubled: marks.filter((states) => {
        return states.length !== 1
      }).map((states) => {
        return states.join(' ')
      }),
    }
  })
}

function exampleOf(path: string): string {
  return new URL(path, 'http://live').pathname.split('/')[2] ?? ''
}

describe('switching around', () => {
  it('ends every switch with the cases listed and the marks of the shown document alone', async () => {
    const page = await newPage(browser, { width: 1440, height: 5800 })
    await page.goto(`${LIVE_URL}/oxsts/basics`)
    const steps = stepsOf(44, 7)
    const done: string[] = []
    for (const { step, wait } of steps) {
      await take(page, step)
      done.push(`${JSON.stringify(step)} +${wait}ms`)
      await page.waitForTimeout(wait)
      if (wait === 0) {
        continue
      }
      const context = done.join('\n')
      await expect.poll(async () => {
        return (await shown(page)).initializing
      }, { timeout: SLOW, message: `initialization ended after\n${context}` }).toBeNull()
      await page.locator('.tabs [role="tab"]').first().click()
      await expect.poll(async () => {
        const now = await shown(page)
        expect(now.doubled, `a mark of one state after\n${context}`).toEqual([])
        expect(now.marks.length, `no more marks than cases after\n${context}`).toBeLessThanOrEqual(now.rows.length)
        return LONG_EXAMPLES.has(exampleOf(now.path)) || now.marks.join(',') === now.rows.join(',')
      }, { timeout: 30_000, message: `the marks are the cases' after\n${context}` }).toBe(true)
    }
  })
})
