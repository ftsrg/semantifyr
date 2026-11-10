/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { createRoot } from 'react-dom/client'
import { beforeAll, describe, expect, it, onTestFinished } from 'vitest'
import { page } from 'vitest/browser'

import type { PortfolioInfo } from '@semantifyr/editor-common'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { ShellRoot } from '../shells/ShellRoot'
import type { TraceSlotProps } from '../shells/traceSlot'
import { caseOf, finished, seedLive, type Seed } from './seededLive'

// Goal: every phone state the mockup draws lays out in the phone's viewport in both themes, one pane at a time,
// with the real editor; the states that a tap opens are reached by the tap

const WIDTH = 390
const HEIGHT = 844

const BARRIER = caseOf('BarrierDownWhileTrainInCrossingViolated', 97)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 112)

const PORTFOLIOS: PortfolioInfo[] = [
  { id: 'smart-full', displayName: 'Smart (full)', description: '', available: true },
  { id: 'all-agree-full', displayName: 'All agree (full)', description: '', available: true },
  { id: 'theta', displayName: 'Theta', description: '', available: true },
  { id: 'spin', displayName: 'Spin', description: '', available: true },
]

function TraceStub({ caseState }: TraceSlotProps): React.JSX.Element {
  return <div className="trace-stub" style={{ height: 240 }}>{`Trace of ${caseState.caseInfo.id}`}</div>
}

function notRun(caseInfo: typeof BARRIER): VerificationCaseState {
  return { caseInfo, status: 'stale' }
}

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase'] = 'idle'): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

const NOT_RUN = discovered([notRun(BARRIER), notRun(TWO_TRAINS)])
const RESULTS = discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done')

interface ScreenState {
  seed: Seed
  act?: () => Promise<void>
  expectState: () => void
}

function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/\u00a0/g, ' ')
}

function count(selector: string): number {
  return document.querySelectorAll(selector).length
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
      expect(text('.phone-orientation')).toContain('Press Verify all')
      expect(text('.model-bar-name')).toBe('level-crossing.sysml')
      expect(count('.phone-bar .button-primary')).toBe(1)
      expect(text('.model-chooser')).toContain('Level crossing')
    },
  },
  'initializing': {
    seed: { initializingAt: 'library' },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(visiblePanes()).toEqual(['Results'])
      expect(text('.phone-init-step.is-current')).toContain('Loading the SysML v2 standard library')
      expect(text('[data-testid=status-message]')).toContain('Initializing SysML v2')
    },
  },
  'initializing-pressed': {
    seed: { initializingAt: 'library', pending: true },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(text('.phone-init-pressed')).toContain('Verify all is pressed')
      expect(count('.phone-bar .button-cancel')).toBe(1)
    },
  },
  'running': {
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'running' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running') },
    act: async () => {
      await tapTab('Results')
    },
    expectState: () => {
      expect(count('.phone-case.status-running')).toBe(1)
      expect(count('.phone-case.status-queued')).toBe(1)
      expect(count('.phone-bar .button-cancel')).toBe(1)
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
  'trace': {
    seed: { verification: RESULTS, selection: BARRIER.id },
    act: async () => {
      await tapTab('Results')
      await tap('.phone-case.status-failed')
    },
    expectState: () => {
      expect(visiblePanes()).toEqual(['Trace'])
      expect(text('.trace-head-title')).toContain(BARRIER.id)
      expect(text('.pane-trace .trace-stub')).toBe(`Trace of ${BARRIER.id}`)
    },
  },
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
      for (const entry of ['Verification', 'Validation', 'Validate automatically', 'About', 'Developer panel', 'Theme']) {
        expect(entries).toContain(entry)
      }
      expect(entries).not.toContain('Open')
      expect(entries).not.toContain('Share')
    },
  },
  'settings': {
    seed: { verification: NOT_RUN, portfolios: PORTFOLIOS },
    act: async () => {
      await tap('.phone-top .phone-icon-button[aria-label=Menu]')
      await expect.poll(() => {
        return document.querySelector('.sheet-bottom .sheet-item')
      }).toBeTruthy()
      document.querySelector<HTMLElement>('.sheet-bottom .sheet-item')!.click()
      await expect.poll(() => {
        return count('.portfolio-choice')
      }).toBe(4)
    },
    expectState: () => {
      expect(document.querySelector('.sheet-bottom .sheet-title')?.textContent).toBe('Verification portfolio')
      expect(text('.portfolio-choice[aria-checked=true]')).toContain('Automatic')
      expect(document.querySelector('.sheet-bottom')?.textContent).toContain('Single engine')
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
  for (const selector of ['.phone-bar', '.pane-header .counts', '.phone-case-outcome', '.sheet-item', '.chooser-languages', '.phone-init-step']) {
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
        const { live, shell } = seedLive(screen.seed)
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        onTestFinished(() => {
          root.unmount()
          container.remove()
        })
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} trace={TraceStub} />)
        await expect.poll(() => {
          return document.querySelector('.monaco-editor .view-line')
        }, { timeout: 60_000 }).toBeTruthy()
        await screen.act?.()
        await new Promise((resolve) => {
          setTimeout(resolve, 600)
        })
        screen.expectState()
        expectLayout()
        await page.screenshot({ path: `../../build/screens/phone-${state}-${colorMode}.png` })
      })
    }
  }
})
