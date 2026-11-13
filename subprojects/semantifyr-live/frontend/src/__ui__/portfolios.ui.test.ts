/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, results, statesOf, test } from './harness'

const LEVEL_CROSSING = '/oxsts/level-crossing?library=gamma'
const BARRIER = '_BarrierDownWhileTrainInCrossing'

async function choose(page: Page, kind: 'Verification' | 'Validation', portfolio: string): Promise<void> {
  await page.getByRole('button', { name: 'Portfolios and validation' }).click()
  await page.locator('.settings-kind', { hasText: kind }).click()
  await page.getByRole('menuitemradio', { name: new RegExp(`^${portfolio}`) }).click()
  await page.keyboard.press('Escape')
}

function portfoliosOf(commands: unknown[]): (string | undefined)[] {
  return commands.map((argument) => {
    return (argument as { portfolio?: string }).portfolio
  })
}

describe('the portfolio settings', () => {
  test('send the verification portfolio with each run and the validation portfolio with the validation that follows a trace', async ({ ui }) => {
    const page = await ui.open(LEVEL_CROSSING)
    await casesListed(page)
    await choose(page, 'Verification', 'Theta')
    await choose(page, 'Validation', 'Spin')

    await results(page).verifyAll.click()

    await expect.poll(async () => {
      return page.locator('.results .validation-mark.is-validated').count()
    }).toBe(2)
    const session = ui.backend.session()
    expect(portfoliosOf(session.commands('oxsts.case.verify'))).toEqual(['theta', 'theta'])
    expect(portfoliosOf(session.commands('oxsts.case.validateWitness'))).toEqual(['spin-safe-dfs', 'spin-safe-dfs'])
  })

  test('with automatic validation off validate nothing, and a row validates its case when asked', async ({ ui }) => {
    const page = await ui.open(LEVEL_CROSSING)
    await casesListed(page)
    await page.getByRole('button', { name: 'Portfolios and validation' }).click()
    await page.getByRole('switch', { name: 'Validate automatically' }).click()
    await page.keyboard.press('Escape')

    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return statesOf(results(page).rows, 'status-')
    }).toEqual(['failed', 'passed'])
    await page.waitForTimeout(500)
    expect(ui.backend.session().commands('oxsts.case.validateWitness')).toEqual([])

    await results(page).row(BARRIER).getByRole('button', { name: `Validate ${BARRIER}` }).click()

    await expect.poll(async () => {
      return results(page).row(BARRIER).locator('.validation-mark').getAttribute('class')
    }).toContain('is-validated')
    expect(portfoliosOf(ui.backend.session().commands('oxsts.case.validateWitness'))).toEqual(['smart'])
  })

  test('show a validation that fails on its row, which validates again when asked', async ({ ui }) => {
    ui.backend.answer('validate', (_request, recorded) => {
      return { ...recorded(), status: 'invalid', verdict: 'passed', message: 'The witness does not reproduce the violation' }
    })
    const page = await ui.open(LEVEL_CROSSING)
    await casesListed(page)

    await results(page).verifyAll.click()

    await expect.poll(async () => {
      return results(page).row(BARRIER).locator('.validation-mark').getAttribute('class')
    }).toContain('is-validation-failed')
    ui.backend.answers = {}
    await results(page).row(BARRIER).getByRole('button', { name: `Validate again ${BARRIER}` }).click()
    await expect.poll(async () => {
      return results(page).row(BARRIER).locator('.validation-mark').getAttribute('class')
    }).toContain('is-validated')
  })
})
