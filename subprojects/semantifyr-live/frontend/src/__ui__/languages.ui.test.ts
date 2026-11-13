/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { servedExamples } from '@semantifyr/editor-common/examples'
import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { BLANK_MODEL } from '../texts'
import { casesListed, LIVE_URL, results, test } from './harness'

const REPOSITORY = fileURLToPath(new URL('../../../../../', import.meta.url))

describe('the language switch', () => {
  test('offers SysML v2, Gamma and OXSTS, and opens a session of the language chosen', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)

    expect(await page.locator('.language-switch [role=group] button').allInnerTexts()).toEqual(['SysML v2', 'Gamma', 'OXSTS'])
    await page.locator('.language-switch [role=group] button', { hasText: 'Gamma' }).click()

    await casesListed(page)
    expect((await ui.backend.openSession()).flavorId).toBe('gamma')
    expect(ui.backend.session(0).closed).not.toBeNull()
  })

  test('lists the OXSTS flavours under OXSTS, and a flavour opens its own session', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)

    await page.locator('.flavour-button').click()
    expect(await page.getByRole('menuitem').locator('.menu-item-label').allInnerTexts()).toEqual(['None', 'Gamma', 'SysML v2'])
    await page.getByRole('menuitem', { name: /SysML v2's semantics/ }).click()

    await casesListed(page)
    expect((await ui.backend.openSession()).flavorId).toBe('oxsts-with-sysmlv2-library')
  })
})

describe('initialization', () => {
  test('shows its steps, each ending in turn, and lists the cases only once it is over', async ({ ui }) => {
    let release = (): void => {}
    const discovered = new Promise<void>((resolve) => {
      release = resolve
    })
    ui.backend.answer('discover', async (_request, recorded) => {
      await discovered
      return recorded()
    })
    const page = await ui.open('/gamma/level-crossing')
    const steps = page.locator('.results .initialization-step')

    await expect.poll(async () => {
      return page.locator('.results .initialization-step.is-current').innerText()
    }).toContain('Finding the verification cases')
    expect(await page.locator('.results .initialization-step.is-done').count()).toBe(3)
    expect(await steps.locator('span:not(.initialization-step-time)').allInnerTexts()).toEqual([
      expect.stringContaining('Connecting to'),
      'Starting the Gamma language server',
      'Initializing the Gamma language server',
      'Finding the verification cases',
    ])
    expect(await results(page).rows.count()).toBe(0)
    release()

    await casesListed(page)
  })
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
  'None',
  'Gamma\'s semantics',
  'SysML v2\'s semantics',
]

// the labels live's menus list, and the blank model
const EXAMPLES = [
  ...new Set(servedExamples.filter((example) => {
    return example.hidden !== true
  }).map((example) => {
    return example.label
  })),
  BLANK_MODEL,
]

const WAITS = [
  0,
  150,
  600,
  2000,
]

// the tall page's editor renders this many lines at most, so a longer model shows the marks of a part of its cases
const LINES_SHOWN = 250

const LONG_EXAMPLES = new Set(servedExamples.filter((example) => {
  return readFileSync(join(REPOSITORY, example.file), 'utf-8').split('\n').length > LINES_SHOWN
}).map((example) => {
  return example.id
}))

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
      const button = results(page).verifyAll
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

describe('switching many times', () => {
  test('ends every switch of language, flavour and example, in varied order and timing, with the cases listed and the marks of the shown document alone', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics', { height: 5800 })
    const done: string[] = []
    for (const { step, wait } of stepsOf(44, 7)) {
      await take(page, step)
      done.push(`${JSON.stringify(step)} +${wait}ms`)
      await page.waitForTimeout(wait)
      if (wait === 0) {
        continue
      }
      const context = done.join('\n')
      await expect.poll(async () => {
        return (await shown(page)).initializing
      }, { timeout: 60_000, message: `initialization ended after\n${context}` }).toBeNull()
      await page.locator('.tabs [role="tab"]').first().click()
      await expect.poll(async () => {
        const now = await shown(page)
        expect(now.doubled, `a mark of one state after\n${context}`).toEqual([])
        expect(now.marks.length, `no more marks than cases after\n${context}`).toBeLessThanOrEqual(now.rows.length)
        return LONG_EXAMPLES.has(exampleOf(now.path)) || now.marks.join(',') === now.rows.join(',')
      }, { timeout: 30_000, message: `the marks are the cases' after\n${context}` }).toBe(true)
    }
  }, 6 * 60 * 1000)
})
