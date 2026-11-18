/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { test, type Ui } from './harness'
import { adminFixture } from './mock/fixtures'

function sessionRows(page: Page) {
  return page.locator('.admin-sessions .admin-session-row').filter({ has: page.getByRole('button', { name: 'Kill', exact: true }) })
}

function calls(ui: Ui, method: string, path: RegExp): number {
  return ui.backend.restCalls.filter((call) => {
    return call.method === method && path.test(call.path)
  }).length
}

async function opened(ui: Ui): Promise<Page> {
  const page = await ui.open('/admin')
  await expect.poll(async () => {
    return sessionRows(page).count()
  }).toBe(adminFixture().status.sessions.length)
  return page
}

describe('the admin page', () => {
  test('kills a session after the second press', async ({ ui }) => {
    const page = await opened(ui)
    const row = sessionRows(page).first()

    await row.getByRole('button', { name: 'Kill', exact: true }).click()
    expect(calls(ui, 'DELETE', /^\/api\/admin\/sessions\//)).toBe(0)
    await page.getByRole('button', { name: 'Kill session' }).click()

    await expect.poll(async () => {
      return calls(ui, 'DELETE', /^\/api\/admin\/sessions\//)
    }).toBe(1)
    await expect.poll(async () => {
      return sessionRows(page).count()
    }).toBe(adminFixture().status.sessions.length - 1)
  })

  test('cancels a verification of a session it opens', async ({ ui }) => {
    const page = await opened(ui)
    const running = adminFixture().status.sessions.findIndex((session) => {
      return session.activeVerifications.length > 0
    })

    await sessionRows(page).nth(running).locator('.admin-session-toggle').click()
    await page.getByRole('list', { name: 'Verifications' }).getByRole('button', { name: 'Cancel' }).first().click()

    await expect.poll(async () => {
      return ui.backend.restCalls.filter((call) => {
        return call.method === 'DELETE' && call.path.startsWith('/api/admin/verifications/')
      }).map((call) => {
        return call.path
      })
    }).toEqual([`/api/admin/verifications/${adminFixture().status.sessions[running]!.activeVerifications[0]!.verificationId}`])
  })

  test('says a refusal of the admin API in words, and tries again when asked', async ({ ui }) => {
    let refused = true
    for (const path of ['whoami', 'config', 'status']) {
      ui.backend.rest(`GET /api/admin/${path}`, (_request, recorded) => {
        return refused ? { status: 403 } : recorded()
      })
    }
    const page = await ui.open('/admin')

    await expect.poll(async () => {
      return page.getByTestId('admin-refused').innerText()
    }).toContain('Not signed in')
    refused = false
    await page.getByRole('button', { name: 'Try again' }).click()

    await expect.poll(async () => {
      return sessionRows(page).count()
    }).toBe(adminFixture().status.sessions.length)
  })
})
