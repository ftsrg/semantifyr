/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, results, test } from './harness'


describe('the language switch', () => {
  test('offers SysML v2, Gamma and the Semantifyr Language, and opens a session of the language chosen', async ({ ui }) => {
    const page = await ui.open('/language/modeling-and-verification')
    await casesListed(page)

    expect(await page.locator('.language-switch [role=group] button').allInnerTexts()).toEqual(['SysML v2', 'Gamma', 'Semantifyr'])
    await page.locator('.language-switch [role=group] button', { hasText: 'Gamma' }).click()

    await casesListed(page)
    expect((await ui.backend.openSession()).flavorId).toBe('gamma')
    expect(ui.backend.session(0).closed).not.toBeNull()
  })

  test('lists the libraries under the Semantifyr Language, and a flavour opens its own session', async ({ ui }) => {
    const page = await ui.open('/language/modeling-and-verification')
    await casesListed(page)

    await page.locator('.flavour-button').click()
    await expect.poll(async () => {
      return page.getByRole('menuitem').locator('.menu-item-label').allInnerTexts()
    }).toEqual(['None', 'Gamma', 'SysML v2'])
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

interface Shown {
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

async function marksAreTheCases(page: Page): Promise<void> {
  await expect.poll(async () => {
    return (await shown(page)).initializing
  }).toBeNull()
  await page.locator('.tabs [role="tab"]').first().click()
  await expect.poll(async () => {
    const now = await shown(page)
    expect(now.doubled).toEqual([])
    return now.marks.join(',') === now.rows.join(',')
  }).toBe(true)
}

describe('a switch', () => {
  test('made before the first discovery answered ends with the cases of the language switched to', async ({ ui }) => {
    let release = (): void => {}
    const discovered = new Promise<void>((resolve) => {
      release = resolve
    })
    ui.backend.answer('discover', async (request, recorded) => {
      if (request.session.flavorId === 'oxsts') {
        await discovered
      }
      return recorded()
    })
    const page = await ui.open('/language/modeling-and-verification', { height: 5800 })
    await expect.poll(async () => {
      return page.locator('.results .initialization-step.is-current').innerText()
    }).toContain('Finding the verification cases')

    await page.locator('.language-switch [role=group] button', { hasText: 'Gamma' }).click()
    release()

    await casesListed(page)
    await marksAreTheCases(page)
  })

  test('after a run leaves in the margin the marks of the shown model alone, there and back', async ({ ui }) => {
    const page = await ui.open('/language/level-crossing', { height: 5800 })
    await casesListed(page)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return (await shown(page)).marks
    }).toEqual(['failed', 'passed'])

    await page.locator('.language-switch [role=group] button', { hasText: 'SysML v2' }).click()
    await casesListed(page)
    await marksAreTheCases(page)

    await page.goBack()
    await casesListed(page)
    await marksAreTheCases(page)
  })

  test('from a read-only document leaves in the margin the marks of the model alone', async ({ ui }) => {
    const page = await ui.open('/language/level-crossing', { height: 5800 })
    await casesListed(page)
    await page.getByRole('button', { name: 'Read-only documents' }).click()
    await page.locator('.documents-group:not([aria-label="Selected case"]):not([aria-label="Model"]) .menu-item').first().click()

    await page.locator('.language-switch [role=group] button', { hasText: 'Gamma' }).click()
    await casesListed(page)

    await marksAreTheCases(page)
  })
})
