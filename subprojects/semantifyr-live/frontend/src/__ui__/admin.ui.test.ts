/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import type { AdminConfigResponse, AdminStatusResponse } from '../lib/api/types'
import { fakeSession } from '../__tests__/fixtures/sessions'
import type { RestAnswer } from './fake/backend'
import { test, type Ui } from './harness'

const CONFIG: AdminConfigResponse = {
  server: {
    port: 8080,
    pingPeriod: 'PT15S',
    pingTimeout: 'PT15S',
    webRootDirectory: '/srv/web',
    wsHandshakesPerPeriod: 30,
    wsHandshakeRatePeriod: 'PT1M',
    maxWsFrameSize: 10485760,
  },
  sessionManager: {
    maxSessionsGlobal: 256,
    semanticLibrariesDirectory: '/srv/libraries',
    rootWorkDirectory: '/srv/work',
  },
  verification: {
    concurrency: 4,
    timeout: 'PT5M',
  },
  portfolios: [
    {
      id: 'smart',
      backends: [
        'theta',
        'spin',
      ],
    },
  ],
}

const RUNNING = 'verification-3'

function status(): AdminStatusResponse {
  return {
    sessions: [
      fakeSession({ sessionId: 'session-1' }),
      fakeSession({
        sessionId: 'session-2',
        runningCount: 1,
        activeVerifications: [
          { verificationId: RUNNING, portfolioId: 'smart', kind: 'Verify', state: 'Running', elapsed: 'PT2S' },
        ],
      }),
      fakeSession({ sessionId: 'session-3', flavorId: 'gamma', languageId: 'gamma' }),
    ],
    load: { activeSessions: 3, maxSessions: 256, runningVerifications: 1, queuedVerifications: 0, verificationPermits: 4, uptime: 'PT1H' },
  }
}

function serveAdmin(ui: Ui, refused?: () => boolean): void {
  const served = status()
  const answer = (body: unknown): RestAnswer => {
    return refused?.() === true ? { status: 403 } : { status: 200, body }
  }
  ui.backend.rest('GET /api/admin/whoami', () => {
    return answer({ user: 'admin' })
  })
  ui.backend.rest('GET /api/admin/config', () => {
    return answer(CONFIG)
  })
  ui.backend.rest('GET /api/admin/status', () => {
    return answer(served)
  })
  ui.backend.rest('DELETE /api/admin/sessions/*', (request) => {
    const killed = decodeURIComponent(new URL(request.url()).pathname.split('/').at(-1) ?? '')
    served.sessions = served.sessions.filter((session) => {
      return session.sessionId !== killed
    })
    return { status: 200 }
  })
  ui.backend.rest('DELETE /api/admin/verifications/*', () => {
    return { status: 200 }
  })
}

function sessionRows(page: Page) {
  return page.locator('.admin-sessions .admin-session-row').filter({ has: page.getByRole('button', { name: 'Kill', exact: true }) })
}

function deleted(ui: Ui): string[] {
  return ui.backend.restCalls.filter((call) => {
    return call.method === 'DELETE'
  }).map((call) => {
    return call.path
  })
}

async function opened(ui: Ui): Promise<Page> {
  const page = await ui.open('/admin')
  await expect.poll(async () => {
    return sessionRows(page).count()
  }).toBe(status().sessions.length)
  return page
}

describe('the admin page', () => {
  test('kills a session after the second press', async ({ ui }) => {
    serveAdmin(ui)
    const page = await opened(ui)
    const row = sessionRows(page).first()

    await row.getByRole('button', { name: 'Kill', exact: true }).click()
    expect(deleted(ui)).toEqual([])
    await page.getByRole('button', { name: 'Kill session' }).click()

    await expect.poll(async () => {
      return deleted(ui)
    }).toEqual(['/api/admin/sessions/session-1'])
    await expect.poll(async () => {
      return sessionRows(page).count()
    }).toBe(status().sessions.length - 1)
  })

  test('cancels a verification of a session it opens', async ({ ui }) => {
    serveAdmin(ui)
    const page = await opened(ui)

    await sessionRows(page).nth(1).locator('.admin-session-toggle').click()
    await page.getByRole('list', { name: 'Verifications' }).getByRole('button', { name: 'Cancel' }).first().click()

    await expect.poll(async () => {
      return deleted(ui)
    }).toEqual([`/api/admin/verifications/${RUNNING}`])
  })

  test('says a refusal of the admin API in words, and tries again when asked', async ({ ui }) => {
    let refused = true
    serveAdmin(ui, () => {
      return refused
    })
    const page = await ui.open('/admin')

    await expect.poll(async () => {
      return page.getByTestId('admin-refused').innerText()
    }).toContain('Not signed in')
    refused = false
    await page.getByRole('button', { name: 'Try again' }).click()

    await expect.poll(async () => {
      return sessionRows(page).count()
    }).toBe(status().sessions.length)
  })
})
