/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { describe, expect, it, onTestFinished } from 'vitest'
import { page, server } from 'vitest/browser'

import { AdminPage } from '../admin/AdminPage'
import { createAdminStore, type AdminStore } from '../admin/adminStore'
import { ApiError, type AdminConfigResponse, type AdminStatusResponse, type InfoResponse, type LiveServerApi, type SessionInfo } from '../lib/api'
import { createSettingsStore } from '../lib/stores/settings'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import { servedFlavors } from '../__tests__/fixtures/flavors'

// Goal: every state of the admin page lays out inside the viewport in both themes, and each state shows the element that tells it apart;
// the proof is the layout itself, measured in a browser, never the pixels

const BACKGROUNDS: Readonly<Record<ResolvedColorMode, string>> = {
  light: 'rgb(241, 238, 231)',
  dark: 'rgb(30, 29, 26)',
}

const SESSIONS: SessionInfo[] = [
  {
    sessionId: '3f9c1a52-7b0e-4d1e-9a64-0c7d2b5e8f11',
    remoteIp: '84.236.12.40',
    flavorId: 'oxsts-with-sysmlv2-library',
    kind: 'full',
    languageId: 'sysml',
    uptime: 'PT12M30S',
    runningCount: 1,
    queuedCount: 1,
    workingDirectory: '/var/lib/semantifyr/work/3f9c1a52-7b0e-4d1e-9a64-0c7d2b5e8f11',
    activeVerifications: [
      { verificationId: 'verification-8812', portfolioId: 'smart', kind: 'Verify', state: 'Running', elapsed: 'PT4.2S' },
      { verificationId: 'verification-8813', portfolioId: 'theta', kind: 'Validate', state: 'Queued', elapsed: 'PT0S' },
    ],
  },
  {
    sessionId: 'a1d4e7f0-22c8-4f3b-b6a9-5e1c0d9f7a23',
    remoteIp: '2001:db8:85a3::8a2e:370:7334',
    flavorId: 'gamma',
    kind: 'full',
    languageId: 'gamma',
    uptime: 'PT2H5M',
    runningCount: 0,
    queuedCount: 0,
    workingDirectory: '/var/lib/semantifyr/work/a1d4e7f0-22c8-4f3b-b6a9-5e1c0d9f7a23',
    activeVerifications: [],
  },
  {
    sessionId: 'c07be9d3-91aa-4c20-8d5f-1b3e6a4c2d90',
    remoteIp: '10.8.0.17',
    flavorId: 'oxsts',
    kind: 'embed',
    languageId: 'oxsts',
    uptime: 'PT41S',
    runningCount: 0,
    queuedCount: 0,
    workingDirectory: '/var/lib/semantifyr/work/c07be9d3-91aa-4c20-8d5f-1b3e6a4c2d90',
    activeVerifications: [],
  },
  {
    sessionId: 'd5f2a8c1-6e47-4b90-a3d1-8c9b0e7f4a56',
    remoteIp: '10.8.0.18',
    flavorId: 'oxsts-with-gamma-library',
    kind: 'edit-only',
    languageId: 'oxsts',
    uptime: 'PT3M9S',
    runningCount: 0,
    queuedCount: 0,
    workingDirectory: '/var/lib/semantifyr/work/d5f2a8c1-6e47-4b90-a3d1-8c9b0e7f4a56',
    activeVerifications: [],
  },
  {
    sessionId: 'e9b3c6a0-4d15-4e78-92f6-7a0d1c5b8e34',
    remoteIp: '84.236.12.40',
    flavorId: 'oxsts-with-sysmlv2-library',
    kind: 'embed',
    languageId: 'sysml',
    uptime: 'PT7M2S',
    runningCount: 0,
    queuedCount: 0,
    workingDirectory: '/var/lib/semantifyr/work/e9b3c6a0-4d15-4e78-92f6-7a0d1c5b8e34',
    activeVerifications: [],
  },
]

const CONFIG: AdminConfigResponse = {
  server: { port: 8080, pingPeriod: 'PT15S', pingTimeout: 'PT30S', webRootDirectory: '/srv/web', wsHandshakesPerPeriod: 10, wsHandshakeRatePeriod: 'PT1M', maxWsFrameSize: 4194304 },
  sessionManager: { maxSessionsGlobal: 256, semanticLibrariesDirectory: '/srv/libraries', rootWorkDirectory: '/var/lib/semantifyr/work' },
  verification: { concurrency: 4, timeout: 'PT1M' },
  portfolios: [
    { id: 'smart', engines: ['nuxmv', 'spin', 'theta'] },
    { id: 'all-agree', engines: ['nuxmv', 'spin', 'theta', 'uppaal'] },
    { id: 'theta', engines: ['theta'] },
    { id: 'nuxmv-ic3-invar', engines: ['nuxmv'] },
    { id: 'spin-safe-dfs', engines: ['spin'] },
    { id: 'uppaal-default', engines: ['uppaal'] },
  ],
}

const INFO: InfoResponse = {
  uptime: 'PT76H',
  startedAt: '2026-11-07T08:00:00Z',
  version: '0.9.4',
  commit: '8f76bf26',
  buildTime: '2026-11-07T07:41:12Z',
  activeSessions: 5,
  maxSessions: 256,
}

function statusOf(sessions: SessionInfo[]): AdminStatusResponse {
  return {
    sessions,
    load: {
      activeSessions: sessions.length,
      maxSessions: 256,
      runningVerifications: sessions.reduce((sum, session) => {
        return sum + session.runningCount
      }, 0),
      queuedVerifications: sessions.reduce((sum, session) => {
        return sum + session.queuedCount
      }, 0),
      verificationPermits: 4,
      uptime: 'PT76H',
    },
  }
}

interface FakeServer {
  api: LiveServerApi
  statusRequests: () => number
}

function fakeServer(sessions: SessionInfo[], refusal?: ApiError): FakeServer {
  let requests = 0
  const api: LiveServerApi = {
    httpBase: 'http://live.example',
    fetchPortfolios: () => {
      return Promise.resolve({
        portfolios: CONFIG.portfolios.map((portfolio, index) => {
          return { id: portfolio.id, displayName: ['Auto', 'All agree', 'Theta', 'nuXmv', 'Spin', 'Uppaal'][index] ?? portfolio.id, description: '', available: portfolio.id !== 'uppaal-default' }
        }),
        defaultPortfolio: 'smart',
      })
    },
    fetchFlavors: () => {
      return Promise.resolve(servedFlavors.map((flavor) => {
        return flavor.id === 'oxsts' ? { ...flavor, displayName: 'OXSTS' } : flavor
      }))
    },
    fetchFlavor: () => {
      return Promise.resolve(null)
    },
    fetchInfo: () => {
      return Promise.resolve(INFO)
    },
    fetchAdminUser: () => {
      return refusal ? Promise.reject(refusal) : Promise.resolve('octocat')
    },
    fetchAdminConfig: () => {
      return Promise.resolve(CONFIG)
    },
    fetchAdminStatus: () => {
      requests++
      return refusal ? Promise.reject(refusal) : Promise.resolve(statusOf(sessions))
    },
    cancelSession: () => {
      return Promise.resolve(true)
    },
    cancelVerification: () => {
      return Promise.resolve(true)
    },
  }
  return {
    api,
    statusRequests: () => {
      return requests
    },
  }
}

interface ScreenState {
  server: FakeServer
  // brings the screen to its state once it is drawn
  act?: (store: AdminStore) => Promise<void>
  // the element that tells this state apart from the first screen
  expectState: () => void
}

function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/\s+/g, ' ').trim()
}

function count(selector: string): number {
  return document.querySelectorAll(selector).length
}

function pause(milliseconds: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds)
  })
}

function states(): Readonly<Record<string, ScreenState>> {
  return {
    'loaded': {
      server: fakeServer(SESSIONS),
      expectState: () => {
        expect(count('.admin-session')).toBe(5)
        expect(count('.admin-details')).toBe(0)
        expect(text('[data-testid=load-sessions]')).toContain('5 of 256')
        expect(text('[data-testid=load-running]')).toContain('1 of 4 permits')
        expect(text('[data-testid=load-queued]')).toContain('1')
        expect(text('[data-testid=load-uptime]')).toContain('3d 4h')
        expect([...document.querySelectorAll('.admin-session .tag[data-kind]')].map((tag) => {
          return tag.getAttribute('data-kind')
        })).toEqual(['full', 'full', 'embed', 'edit-only', 'embed'])
        expect(text('.admin-deployed')).toContain('0.9.4')
        expect(text('.admin-deployed')).toContain('Verifier time')
        expect(count('.admin-deployed .admin-engines .tag')).toBeGreaterThan(8)
        expect(text('.admin-deployed')).toContain('unavailable')
        expect(text('.admin-header')).toContain('Signed in as octocat')
        expect(count('.admin-header a[href*=grafana]')).toBe(1)
        expect([...document.querySelectorAll('.admin-deployed .admin-row-name')].map((row) => {
          return `${row.textContent}: ${row.parentElement?.querySelector('.admin-row-count')?.textContent ?? ''}`
        }).filter((row) => {
          return /OXSTS|Gamma|SysML/.test(row)
        })).toEqual([
          'OXSTS: 1',
          'OXSTS with the Gamma library: 1',
          'OXSTS with the SysML v2 library: 0',
          'Gamma: 1',
          'SysML v2: 2',
        ])
        expect(text('.admin-session .admin-flavour-name')).toBe('SysML v2')
      },
    },
    'session-open': {
      server: fakeServer(SESSIONS),
      act: async () => {
        await page.getByRole('button', { name: /Full\s+SysML v2/ }).first().click()
      },
      expectState: () => {
        expect(count('.admin-session.is-open')).toBe(1)
        expect(count('.admin-verification')).toBe(2)
        expect(text('.admin-verification.status-running')).toContain('Verifies')
        expect(text('.admin-verification.status-queued')).toContain('Validates')
        expect(count('.admin-verification .button')).toBe(2)
        expect(text('.admin-directory')).toContain('/var/lib/semantifyr/work/3f9c1a52')
      },
    },
    'filtered': {
      server: fakeServer(SESSIONS),
      act: async () => {
        await page.getByLabelText('Filter sessions').fill('embed')
      },
      expectState: () => {
        expect(count('.admin-session')).toBe(2)
        expect(text('[data-testid=sessions-shown]')).toBe('2 of 5')
      },
    },
    'filter-without-match': {
      server: fakeServer(SESSIONS),
      act: async () => {
        await page.getByLabelText('Filter sessions').fill('nothing here')
      },
      expectState: () => {
        expect(count('.admin-session')).toBe(0)
        expect(text('[data-testid=sessions-unmatched]')).toContain('No session matches "nothing here"')
      },
    },
    'paused': {
      server: fakeServer(SESSIONS),
      act: async () => {
        await page.getByRole('button', { name: 'Pause auto-refresh' }).click()
      },
      expectState: () => {
        expect(text('[data-testid=admin-refresh-state]')).toBe('Paused')
        expect(count('button[aria-label="Resume auto-refresh"][aria-pressed=true]')).toBe(1)
      },
    },
    'no-sessions': {
      server: fakeServer([]),
      expectState: () => {
        expect(text('[data-testid=sessions-empty]')).toContain('No sessions are open')
        expect(text('[data-testid=load-sessions]')).toContain('0 of 256')
        expect(count('.admin-session')).toBe(0)
      },
    },
    'refused': {
      server: fakeServer(SESSIONS, new ApiError(403, 'Not signed in through the proxy')),
      expectState: () => {
        expect(text('[data-testid=admin-refused]')).toContain('Not signed in')
        expect(text('[data-testid=admin-refused]')).toContain('Reload the page to sign in')
        expect(count('.admin-load')).toBe(0)
        expect(count('.admin-session')).toBe(0)
      },
    },
  }
}

function expectLayout(colorMode: ResolvedColorMode): void {
  const root = document.documentElement
  expect(root.scrollWidth, 'horizontal overflow').toBeLessThanOrEqual(root.clientWidth)
  for (const selector of ['.admin-header-group', '.admin-figure', '.admin-panel-header']) {
    for (const element of document.querySelectorAll(selector)) {
      const box = element.getBoundingClientRect()
      expect(box.right, `${selector} right`).toBeLessThanOrEqual(window.innerWidth)
      expect(element.scrollWidth, `${selector} clipped`).toBeLessThanOrEqual(element.clientWidth + 1)
    }
  }
  expect(getComputedStyle(document.body).backgroundColor).toBe(BACKGROUNDS[colorMode])
}

async function showState(state: string, colorMode: ResolvedColorMode, picture: string): Promise<void> {
  const screen = states()[state]!
  const store = createAdminStore(screen.server.api, 1000)
  const container = document.createElement('div')
  document.body.append(container)
  const root = createRoot(container)
  onTestFinished(() => {
    root.unmount()
    container.remove()
  })
  root.render(
    <AdminPage
      store={store}
      settings={createSettingsStore()}
      colorMode={colorMode}
      links={{ grafana: 'https://grafana.example/', signOut: 'https://auth.example/oauth2/sign_out' }}
    />,
  )
  await expect.poll(() => {
    return document.querySelector('.admin-session, [data-testid=sessions-empty], [data-testid=admin-refused]')
  }, { timeout: 10_000 }).toBeTruthy()
  await screen.act?.(store)
  await pause(400)
  screen.expectState()
  expectLayout(colorMode)
  if (state === 'paused') {
    const before = screen.server.statusRequests()
    await pause(2500)
    expect(screen.server.statusRequests(), 'requests while paused').toBe(before)
  }
  await page.screenshot({ path: `../../build/screens/${server.browser}/${picture}.png` })
}

describe('the admin page', () => {
  for (const colorMode of ['light', 'dark'] as const) {
    for (const state of Object.keys(states())) {
      it(`lays out ${state} in the ${colorMode} theme`, async () => {
        await showState(state, colorMode, `admin-${state}-${colorMode}`)
      })
    }
  }

  it('lays out an open session at a tablet\'s width', async () => {
    await page.viewport(768, 1024)
    onTestFinished(async () => {
      await page.viewport(1440, 900)
    })
    await showState('session-open', 'light', 'admin-session-open-tablet-light')
  })
})
