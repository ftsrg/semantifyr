/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { test } from './harness'

const PHONE = { width: 390, height: 844, touch: true }

function phone(page: Page) {
  return {
    shell: page.getByTestId('phone-shell'),
    tab: (label: string) => {
      return page.locator('.phone-bar .phone-tab', { hasText: label })
    },
    verify: page.locator('.phone-verify'),
    lines: page.locator('.view-lines').first(),
  }
}

async function ready(page: Page): Promise<void> {
  await expect.poll(async () => {
    return phone(page).lines.innerText()
  }).toContain('package')
}

describe('the phone', () => {
  test('shows one pane at a time, the results and the trace of a run among them', async ({ ui }) => {
    const page = await ui.open('/oxsts/level-crossing?library=gamma', PHONE)
    await ready(page)
    expect(await phone(page).shell.getAttribute('data-pane')).toBe('model')

    await phone(page).verify.click()
    await phone(page).tab('Results').click()
    await expect.poll(async () => {
      return page.locator('.case-row.status-failed, .phone-case.status-failed, [class*="status-failed"]').count()
    }).toBeGreaterThan(0)
    expect(await phone(page).shell.getAttribute('data-pane')).toBe('results')
    await expect.poll(async () => {
      return phone(page).tab('Trace').isEnabled()
    }).toBe(true)
    await phone(page).tab('Trace').click()

    await expect.poll(async () => {
      return page.locator('.case-trace').count()
    }).toBe(1)
    expect(await phone(page).shell.getAttribute('data-pane')).toBe('trace')
    expect(await phone(page).lines.isVisible()).toBe(false)
  })

  test('opens the menu as a sheet, and a model is chosen from its chooser', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics', PHONE)
    await ready(page)

    await page.getByRole('button', { name: 'Menu' }).click()
    await expect.poll(async () => {
      return page.getByRole('dialog', { name: 'Menu' }).isVisible()
    }).toBe(true)
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: 'Choose a model' }).click()
    const chooser = page.getByRole('dialog', { name: 'Choose a model' })
    await chooser.getByRole('group', { name: 'Language' }).getByRole('button', { name: /Gamma/ }).click()
    await chooser.locator('.chooser-item', { hasText: 'Crossroads' }).click()

    await expect.poll(async () => {
      return page.title()
    }).toBe('Crossroads - Gamma - Semantifyr Live')
  })
})

describe('touch input', () => {
  for (const [shell, viewport] of [
    ['phone', PHONE],
    ['tablet', { width: 1024, height: 768, touch: true }],
  ] as const) {
    test(`locks the model of the ${shell} until Edit is pressed, and again when Done`, async ({ ui }) => {
      const page = await ui.open('/oxsts/basics', viewport)
      await ready(page)
      const before = await phone(page).lines.innerText()

      await phone(page).lines.tap()
      await page.keyboard.insertText('locked ')
      await page.waitForTimeout(300)
      expect(await phone(page).lines.innerText()).toBe(before)
      await page.getByRole('button', { name: 'Edit' }).first().tap()
      await phone(page).lines.tap()
      await page.keyboard.insertText('unlocked ')

      await expect.poll(async () => {
        return phone(page).lines.innerText()
      }).toContain('unlocked')
      await page.getByRole('button', { name: 'Done' }).first().tap()
      await page.keyboard.insertText('again ')
      await page.waitForTimeout(300)
      expect(await phone(page).lines.innerText()).not.toContain('again')
    })
  }
})
