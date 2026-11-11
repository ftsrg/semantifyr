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
  test('lists the sessions, and the filter keeps those it matches', async ({ ui }) => {
    const page = await opened(ui)
    const [full, embed] = adminFixture().status.sessions

    await page.getByRole('textbox', { name: 'Filter sessions' }).fill(embed!.kind)

    await expect.poll(async () => {
      return sessionRows(page).count()
    }).toBe(1)
    expect(await sessionRows(page).innerText()).toContain(embed!.sessionId.slice(0, 8))
    await page.getByRole('textbox', { name: 'Filter sessions' }).fill('no-such-session')
    await expect.poll(async () => {
      return page.getByTestId('sessions-unmatched').innerText()
    }).toContain('No session matches')
    expect(full).toBeDefined()
  })

  test('kills a session after the second press', async ({ ui }) => {
    const page = await opened(ui)
    const row = sessionRows(page).first()

    await row.getByRole('button', { name: 'Kill', exact: true }).click()
    expect(calls(ui, 'DELETE', /^\/api\/admin\/sessions\//)).toBe(0)
    await row.getByRole('button', { name: 'Kill session' }).click()

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
    await page.getByRole('list', { name: 'Verifications' }).getByRole('button', { name: 'Cancel' }).click()

    await expect.poll(async () => {
      return ui.backend.restCalls.filter((call) => {
        return call.method === 'DELETE' && call.path.startsWith('/api/admin/verifications/')
      }).map((call) => {
        return call.path
      })
    }).toEqual([`/api/admin/verifications/${adminFixture().status.sessions[running]!.activeVerifications[0]!.verificationId}`])
  })

  test('stops refreshing while paused and refreshes at once when resumed', async ({ ui }) => {
    const page = await opened(ui)

    await page.getByRole('button', { name: 'Pause auto-refresh' }).click()
    await page.waitForTimeout(300)
    const paused = calls(ui, 'GET', /^\/api\/admin\/status$/)
    await page.waitForTimeout(2500)
    expect(calls(ui, 'GET', /^\/api\/admin\/status$/)).toBe(paused)
    await page.getByRole('button', { name: 'Resume auto-refresh' }).click()

    await expect.poll(async () => {
      return calls(ui, 'GET', /^\/api\/admin\/status$/)
    }).toBeGreaterThan(paused)
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
