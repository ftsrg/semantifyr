/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { beforeAll, describe, expect, it, onTestFinished } from 'vitest'
import { page, server } from 'vitest/browser'

import type { PortfolioInfo } from '@semantifyr/editor-common'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { ShellRoot } from '../shells/ShellRoot'
import { CaseTrace } from '../trace/CaseTrace'
import { caseOf, finished, seedLive, type Seed, type Seeded } from './seededLive'
import { caseState, LANGUAGES, seedWithTrace } from './traces'
import { noCases } from '../texts'

// Goal: every phone state the mockup draws lays out in the phone's viewport in both themes, one pane at a time,
// with the real editor; the states that a tap opens are reached by the tap

const WIDTH = 390
const HEIGHT = 844

const BARRIER = caseOf('BarrierDownWhileTrainInCrossing', 102)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 117)

const PORTFOLIOS: PortfolioInfo[] = [
  { id: 'smart', displayName: 'Smart', description: 'Runs the installed backends side by side and takes the first trusted answer.', available: true },
  { id: 'all-agree', displayName: 'All agree', description: 'Runs every installed backend and requires their trusted answers to agree.', available: true },
  { id: 'theta', displayName: 'Theta', description: 'Runs Theta\'s CEGAR and k-induction configurations side by side and takes the first answer.', available: true },
  { id: 'spin', displayName: 'Spin', description: 'Runs the safe-dfs configuration of spin alone.', available: true },
]

const SYSML = LANGUAGES.sysml!
const TRACED = caseState(SYSML, { validated: true, recorded: true })

function notRun(caseInfo: typeof BARRIER): VerificationCaseState {
  return { caseInfo, status: 'stale' }
}

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase'] = 'idle'): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

const NOT_RUN = discovered([notRun(BARRIER), notRun(TWO_TRAINS)])
const RESULTS = discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done')

interface ScreenState {
  seed: Seed | (() => Seeded)
  act?: () => Promise<void>
  expectState: () => void
}

function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/\u00a0/g, ' ')
}

function count(selector: string): number {
  return document.querySelectorAll(selector).length
}

// the lines of the editor in the element wrap within its width
function wrapped(selector: string): boolean {
  const editor = document.querySelector(`${selector} .monaco-editor`)
  const lines = document.querySelector(`${selector} .monaco-editor .view-lines`)
  return editor !== null && lines !== null && lines.scrollWidth <= editor.clientWidth
}

function visiblePanes(): string[] {
  return [...document.querySelectorAll<HTMLElement>('.phone .pane')].filter((pane) => {
    return !pane.hidden
  }).map((pane) => {
    return pane.getAttribute('aria-label') ?? ''
  })
}

async function tap(selector: string): Promise<void> {
  await expect.poll(() => {
    return document.querySelector(selector)
  }).toBeTruthy()
  document.querySelector<HTMLElement>(selector)!.click()
}

async function tapTab(label: string): Promise<void> {
  const tab = [...document.querySelectorAll<HTMLElement>('.phone-tab')].find((candidate) => {
    return candidate.innerText.includes(label)
  })
  expect(tab, label).toBeDefined()
  tab!.click()
  await expect.poll(() => {
    return visiblePanes()
  }).toEqual([label])
}

const STATES: Readonly<Record<string, ScreenState>> = {
  'model': {
    seed: { verification: NOT_RUN },
    expectState: () => {
      expect(visiblePanes()).toEqual(['Model'])
      expect(count('.phone-orientation')).toBe(0)
      expect(text('.model-bar-name')).toBe('level-crossing.sysml')
      expect(count('.phone-bar .button-primary')).toBe(1)
      expect(text('.model-chooser')).toContain('Level crossing')
      expect(wrapped('.pane-model')).toBe(true)
    },
  },
  'initializing': {
    seed: { initializingAt: 'cases' },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(visiblePanes()).toEqual(['Results'])
      expect(count('.phone-init-step.is-current')).toBe(2)
      expect(text('.phone-init-steps')).toContain('Initializing the SysML v2 language server')
      expect(text('[data-testid=status-message]')).toContain('Initializing SysML v2')
    },
  },
  'cases-while-initializing': {
    seed: { initializingAt: 'initialize', verification: discovered([{ caseInfo: BARRIER, status: 'queued' }, notRun(TWO_TRAINS)]), queued: { caseId: BARRIER.id } },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(count('.phone-case.status-queued')).toBe(1)
      expect(count('.phone-case.status-not-run')).toBe(1)
      expect(count('.phone-init')).toBe(0)
      expect(text('[data-testid=status-message]')).toContain(`Verify ${BARRIER.id} waits: initializing the SysML v2 language server`)
    },
  },
  'initialization-timed-out': {
    seed: { initializingAt: 'initialize', initializationFailure: 'Initializing the SysML v2 language server did not end within 60 s.', verification: NOT_RUN },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(count('.phone-case.status-not-run')).toBe(2)
      expect(text('[data-testid=status-message]')).toContain('Initializing the SysML v2 language server did not end within 60 s.')
    },
  },
  'initializing-pressed': {
    seed: { initializingAt: 'cases', queued: { caseId: null } },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(text('.phone-init-pressed')).toContain('Verify all is pressed')
      expect(count('.phone-bar .button-cancel')).toBe(1)
    },
  },
  'running': {
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'running', serverState: 'Running' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running') },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(count('.phone-case.status-running')).toBe(1)
      expect(count('.phone-case.status-queued')).toBe(1)
      expect(count('.phone-bar .button-cancel')).toBe(1)
    },
  },
  'server-busy': {
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'running', serverState: 'Queued' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running') },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(count('.phone-case.status-queued')).toBe(2)
      expect(count('.phone-case.status-running')).toBe(0)
      expect(text('[data-testid=status-message]')).toContain(`The server is busy with other verifications: ${BARRIER.id} waits`)
    },
  },
  'verify-all': {
    seed: { verification: NOT_RUN },
    act: async () => {
      await tap('.phone-bar .button-primary')
    },
    expectState: () => {
      expect(visiblePanes()).toEqual(['Results'])
      expect(count('.phone-case')).toBe(2)
    },
  },
  'results': {
    seed: { verification: RESULTS, selection: BARRIER.id },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(count('.phone-case.status-failed')).toBe(1)
      expect(count('.phone-case.status-passed')).toBe(1)
      expect(text('.pane-header .counts')).toContain('of 2')
      expect(count('.phone-tab .tab-dot.status-failed')).toBe(1)
    },
  },
  'no-cases': {
    seed: { verification: { phase: 'idle', discovery: 'discovered', cases: [] } },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(text('.pane-hint')).toBe(noCases('sysml'))
    },
  },
  'trace': {
    seed: () => {
      return seedWithTrace(SYSML, TRACED)
    },
    act: async () => {
      await tapTab('Results')
      await tap('.phone-case.status-failed')
      // the trace's editor wraps its lines once it has measured its pane, which a slower engine does after the settling time
      await expect.poll(() => {
        return wrapped('.pane-trace .trace-frame')
      }).toBe(true)
    },
    expectState: () => {
      expect(visiblePanes()).toEqual(['Trace'])
      expect(text('.trace-head-title')).toContain(TRACED.caseInfo.id)
      expect(text('.pane-trace .trace-views [aria-pressed=true]')).toBe('SysML trace')
      expect(text('.pane-trace [data-testid=run-details]')).toContain('decided by Theta')
      expect(wrapped('.pane-trace .trace-frame')).toBe(true)
    },
  },
  ...Object.fromEntries([
    ['steps', '.pane-trace [data-testid=trace-steps] .step'],
    ['calls', '.pane-trace [data-testid=trace-calls] .call'],
    ['witness', '.pane-trace .trace-frame'],
  ].map(([view, shown]) => {
    return [`trace-${view}`, {
      seed: () => {
        return seedWithTrace(SYSML, TRACED)
      },
      act: async () => {
        await tapTab('Results')
        await tap('.phone-case.status-failed')
        await tap(`.pane-trace .trace-views [data-view=${view}]`)
      },
      expectState: () => {
        expect(count(`.pane-trace .trace-views [data-view=${view}][aria-pressed=true]`)).toBe(1)
        expect(count(shown!)).toBeGreaterThan(0)
      },
    }]
  })),
  'chooser': {
    seed: { verification: NOT_RUN },
    act: async () => {
      await tap('.model-chooser')
    },
    expectState: () => {
      expect(count('.sheet-full .chooser-languages .segmented-option')).toBe(3)
      expect(count('.sheet-full .chooser-item.is-current')).toBe(1)
      expect(text('.sheet-full .chooser-item.is-current')).toContain('Level crossing')
    },
  },
  'menu': {
    seed: { verification: NOT_RUN, portfolios: PORTFOLIOS },
    act: async () => {
      await tap('.phone-top .phone-icon-button[aria-label=Menu]')
    },
    expectState: () => {
      const entries = text('.sheet-bottom')
      for (const entry of ['Open a file', 'Share', 'Verification', 'Validation', 'Validate automatically', 'About', 'Developer panel', 'Theme']) {
        expect(entries).toContain(entry)
      }
      expect(text('.sheet-bottom .sheet-item:nth-of-type(2)')).toMatch(/^Share\s+Model$/)
    },
  },
  'settings': {
    seed: { verification: NOT_RUN, portfolios: PORTFOLIOS },
    act: async () => {
      await tap('.phone-top .phone-icon-button[aria-label=Menu]')
      await expect.poll(() => {
        return document.querySelector('.sheet-bottom .sheet-item')
      }).toBeTruthy()
      const verification = [...document.querySelectorAll<HTMLElement>('.sheet-bottom .sheet-item')].find((item) => {
        return item.innerText.startsWith('Verification')
      })
      verification!.click()
      await expect.poll(() => {
        return count('.portfolio-choice')
      }).toBe(4)
    },
    expectState: () => {
      expect(document.querySelector('.sheet-bottom .sheet-title')?.textContent).toBe('Verification portfolio')
      expect(text('.portfolio-choice[aria-checked=true]')).toContain('Smart')
      expect(document.querySelector('.sheet-bottom')?.textContent).toContain('One backend')
    },
  },
}

function expectLayout(): void {
  const root = document.documentElement
  expect(root.scrollWidth, 'horizontal overflow').toBeLessThanOrEqual(root.clientWidth)
  const phone = document.querySelector('[data-testid=phone-shell]')!.getBoundingClientRect()
  expect(phone.width).toBe(WIDTH)
  expect(phone.height).toBe(HEIGHT)
  const bottom = document.querySelector('.phone-bottom')!.getBoundingClientRect()
  expect(bottom.bottom).toBeLessThanOrEqual(HEIGHT)
  // every view of the trace is reachable without scrolling sideways
  for (const option of document.querySelectorAll('.trace-views .segmented-option')) {
    const box = option.getBoundingClientRect()
    expect(box.left, `${option.textContent} left`).toBeGreaterThanOrEqual(0)
    expect(box.right, `${option.textContent} right`).toBeLessThanOrEqual(WIDTH)
  }
  for (const selector of ['.phone-bar', '.pane-header .counts', '.phone-case-outcome', '.sheet-item', '.chooser-languages', '.phone-init-step', '.trace-views']) {
    for (const element of document.querySelectorAll(selector)) {
      expect(element.scrollWidth, `${selector} clipped`).toBeLessThanOrEqual(element.clientWidth + 1)
    }
  }
}

describe('the phone shell', () => {
  beforeAll(async () => {
    await page.viewport(WIDTH, HEIGHT)
  })

  for (const colorMode of ['light', 'dark'] as const satisfies readonly ResolvedColorMode[]) {
    for (const [state, screen] of Object.entries(STATES)) {
      it(`lays out ${state} in the ${colorMode} theme`, async () => {
        const { live, shell } = typeof screen.seed === 'function' ? screen.seed() : seedLive(screen.seed)
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        onTestFinished(() => {
          root.unmount()
          container.remove()
        })
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} trace={CaseTrace} />)
        await expect.poll(() => {
          return document.querySelector('.monaco-editor .view-line')
        }, { timeout: 60_000 }).toBeTruthy()
        await screen.act?.()
        await new Promise((resolve) => {
          setTimeout(resolve, 600)
        })
        screen.expectState()
        expectLayout()
        await page.screenshot({ path: `../../build/screens/${server.browser}/phone-${state}-${colorMode}.png` })
      })
    }
  }
})
