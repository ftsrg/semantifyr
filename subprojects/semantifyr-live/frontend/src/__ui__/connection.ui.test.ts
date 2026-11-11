/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect } from 'vitest'

import { casesListed, editor, results, seenStatusTexts, statesOf, test, watchStatusTexts } from './harness'

const TERMINATED = 4000
const TOO_MANY_SESSIONS = 4429
const SERVER_ERROR = 1011

describe('a server that does not answer at page load', () => {
  for (const [address, text] of [
    ['/sysml/level-crossing', 'part def'],
    ['/gamma/level-crossing', 'statechart'],
    ['/oxsts/basics', 'package'],
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
  test('is retried with a growing wait, said at the indicator and the status line, and starts once the server takes it', async ({ ui }) => {
    let refusals = 3
    ui.backend.connect = () => {
      if (refusals > 0) {
        refusals--
        return { code: TOO_MANY_SESSIONS, reason: 'Too many sessions' }
      }
      return 'accept'
    }
    const page = await ui.open('/oxsts/basics', { before: watchStatusTexts })

    await expect.poll(async () => {
      return page.locator('.connection').innerText()
    }).toContain('refused, retrying')
    await casesListed(page)

    expect(await seenStatusTexts(page)).toEqual(expect.arrayContaining([
      expect.stringContaining('The server refused the session: it is at capacity. Retrying in 1 s'),
      expect.stringContaining('Retrying in 4 s; the wait grows with each refusal'),
    ]))
    expect(ui.backend.sessions.map((session) => {
      return session.closed?.code ?? 'open'
    })).toEqual([TOO_MANY_SESSIONS, TOO_MANY_SESSIONS, TOO_MANY_SESSIONS, 'open'])
  })
})

describe('a session the server terminates', () => {
  test('stays terminated with the model on screen until Reconnect is pressed, which starts a new session', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)

    ui.backend.session().close(TERMINATED, 'Session terminated by an administrator')

    await expect.poll(async () => {
      return page.locator('.connection').innerText()
    }).toContain('terminated')
    await expect.poll(async () => {
      return results(page).status.innerText()
    }).toContain('Session terminated by an administrator. Not reconnecting until you ask.')
    await page.waitForTimeout(3000)
    expect(ui.backend.sessions).toHaveLength(1)
    expect(await editor(page).lines.innerText()).toContain('package')

    await results(page).status.getByRole('button', { name: 'Reconnect' }).click()

    await expect.poll(async () => {
      return ui.backend.sessions.length
    }).toBe(2)
    await casesListed(page)
  })
})

describe('a lost connection', () => {
  test('is retried at once and ends a run, a case without a verdict as not run and cancelled', async ({ ui }) => {
    ui.backend.answer('verify', (request) => {
      return new Promise((_resolve, reject) => {
        request.signal.addEventListener('abort', () => {
          reject(new Error('the socket closed'))
        })
      })
    })
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return statesOf(results(page).rows, 'status-')
    }).toContain('running')

    ui.backend.session().close(SERVER_ERROR, 'Internal error')

    await expect.poll(async () => {
      return results(page).status.innerText()
    }).toMatch(/Connection lost. Reconnecting/)
    await expect.poll(async () => {
      return statesOf(results(page).rows, 'status-')
    }).toEqual(['not-run', 'not-run'])
    await expect.poll(async () => {
      return results(page).rows.first().locator('.case-message').innerText()
    }).toContain('cancelled')
    await expect.poll(async () => {
      return ui.backend.sessions.length
    }).toBe(2)
    await expect.poll(async () => {
      return page.locator('.connection').getAttribute('class')
    }).toContain('is-connected')
  })
})
