/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect } from 'vitest'

import { casesListed, results, test } from './harness'

const TERMINATED = 4000
const TOO_MANY_SESSIONS = 4429
const SERVER_ERROR = 1011

describe('a server that does not answer at page load', () => {
  for (const [address, text] of [
    ['/sysml/level-crossing', 'part def'],
    ['/gamma/level-crossing', 'statechart'],
    ['/language/intersection', 'package'],
  ] as const) {
    test(`shows the model of ${address} and retries until it answers`, async ({ ui }) => {
      let reachable = false
      ui.backend.rest('GET /api/flavors', (_request, recorded) => {
        return reachable ? recorded() : 'unreachable'
      })
      const page = await ui.open(address)

      await expect.poll(async () => {
        return page.locator('.connection').innerText()
      }).toContain('retrying')
      await expect.poll(async () => {
        return page.locator('section.editor').innerText()
      }).toContain(text)
      expect(ui.backend.sessions).toEqual([])

      reachable = true

      await casesListed(page)
      expect(ui.backend.sessions.length).toBeGreaterThan(0)
    })
  }
})

describe('a session the server refuses', () => {
  test('is retried, said at the indicator, and starts once the server takes it', async ({ ui }) => {
    let refusals = 1
    ui.backend.connect = () => {
      if (refusals > 0) {
        refusals--
        return { code: TOO_MANY_SESSIONS, reason: 'Too many sessions' }
      }
      return 'accept'
    }
    const page = await ui.open('/language/intersection')

    await expect.poll(async () => {
      return page.locator('.connection').innerText()
    }).toContain('refused, retrying')
    await casesListed(page)

    expect(ui.backend.sessions.map((session) => {
      return session.closed?.code ?? 'open'
    })).toEqual([TOO_MANY_SESSIONS, 'open'])
  })
})

describe('a session the server terminates', () => {
  test('says it ended the session, and Reconnect starts a new one', async ({ ui }) => {
    const page = await ui.open('/language/intersection')
    await casesListed(page)

    ui.backend.session().close(TERMINATED, 'Session terminated by an administrator')

    await expect.poll(async () => {
      return results(page).status.innerText()
    }).toContain('Session terminated by an administrator. Not reconnecting until you ask.')

    await results(page).status.getByRole('button', { name: 'Reconnect' }).click()

    await expect.poll(async () => {
      return ui.backend.sessions.length
    }).toBe(2)
    await casesListed(page)
  })
})

describe('a lost connection', () => {
  test('is retried and starts a new session', async ({ ui }) => {
    const page = await ui.open('/language/intersection')
    await casesListed(page)

    ui.backend.session().close(SERVER_ERROR, 'Internal error')

    await expect.poll(async () => {
      return ui.backend.sessions.length
    }).toBe(2)
    await expect.poll(async () => {
      return page.locator('.connection').getAttribute('class')
    }).toContain('is-connected')
  })
})
