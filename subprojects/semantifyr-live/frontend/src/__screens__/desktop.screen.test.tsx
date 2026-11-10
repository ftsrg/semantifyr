/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { describe, expect, it } from 'vitest'
import { page } from 'vitest/browser'

import { connectionLost } from '../lib/stores/connection'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { modelOxstsTabOf } from '../lib/workspace/libraryTabs'
import { ShellRoot } from '../shells/ShellRoot'
import { caseOf, CONNECTED, finished, seedLive, type Seed } from './seededLive'

// Goal: every desktop state the mockup draws lays out inside the viewport in both themes, with the real editor;
// the proof is the layout itself, measured in a browser, never the pixels

const BARRIER = caseOf('BarrierDownWhileTrainInCrossingViolated', 97)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 112)

const BACKGROUNDS: Readonly<Record<ResolvedColorMode, string>> = {
  light: 'rgb(241, 238, 231)',
  dark: 'rgb(30, 29, 26)',
}

// elements whose text must show whole; a case name may end in an ellipsis by design
const UNCLIPPED = [
  '.toolbar-group',
  '.language-switch',
  '.results-header',
  '.counts',
  '.case-outcome',
  '.status-message',
  '.status-end',
  '.orientation-text',
  '.initialization-step',
]

function notRun(caseInfo: typeof BARRIER): VerificationCaseState {
  return { caseInfo, status: 'stale' }
}

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase'] = 'idle'): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

const RESULTS = discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done')

const OUTDATED = discovered(RESULTS.cases.map((caseState) => {
  return { ...caseState, outdated: true }
}), 'done')

const STATES: Readonly<Record<string, Seed>> = {
  'first': { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]) },
  'initializing': { initializingAt: 'library' },
  'initializing-pressed': { initializingAt: 'library', pending: true },
  'running': { verification: discovered([{ caseInfo: BARRIER, status: 'running' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running') },
  'results': { verification: RESULTS, selection: BARRIER.id, timing: { key: 'sysml#level_crossing', caseId: null, startedAt: Date.now() - 3700, finishedAt: Date.now() } },
  'outdated': { verification: OUTDATED, selection: BARRIER.id },
  'address-unknown-example': { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]), address: { kind: 'example', requested: 'level-crosing', flavorId: 'sysml', exampleId: 'level_crossing' } },
  'connection-terminated': { verification: RESULTS, connection: connectionLost(CONNECTED, 'failed', 'Session terminated', { terminated: true }) },
  'server-unreachable': { catalog: 'waiting' },
  'collapsed': { verification: RESULTS, settings: { resultsColumnCollapsed: true } },
  'tab-model-oxsts': { verification: RESULTS, tab: modelOxstsTabOf('level-crossing.sysml', 'package semantifyr::sysml::model\n\nclass LevelCrossing {\n}\n') },
  'failure-problems': {
    verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]),
    verifyBlocked: true,
    problems: [
      { severity: 'error', startLine: 60, startColumn: 4, endLine: 60, endColumn: 9, message: 'missing \';\' at \'}\'', source: 'sysml' },
      { severity: 'warning', startLine: 9, startColumn: 9, endLine: 9, endColumn: 13, message: 'Item \'Lift\' is sent but never accepted.', source: 'sysml' },
    ],
  },
  'gamma': { flavorId: 'gamma', verification: discovered([notRun(caseOf('BarrierDownWhileTrainInCrossing', 90, 'file:///workspace/snippet.gamma'))]) },
  'oxsts': { flavorId: 'oxsts', exampleId: 'level-crossing', verification: discovered([notRun(caseOf('BarrierDownWhileTrainInCrossing', 90, 'file:///workspace/snippet.oxsts'))]) },
}

function expectInViewport(selector: string): void {
  const element = document.querySelector(selector)
  expect(element, selector).not.toBeNull()
  const box = element!.getBoundingClientRect()
  expect(box.left, `${selector} left`).toBeGreaterThanOrEqual(0)
  expect(box.top, `${selector} top`).toBeGreaterThanOrEqual(0)
  expect(box.right, `${selector} right`).toBeLessThanOrEqual(window.innerWidth)
  expect(box.bottom, `${selector} bottom`).toBeLessThanOrEqual(window.innerHeight)
}

function expectLayout(colorMode: ResolvedColorMode, waiting: boolean): void {
  const root = document.documentElement
  expect(root.scrollWidth, 'horizontal overflow').toBeLessThanOrEqual(root.clientWidth)
  expectInViewport('.results-header .button-primary, .results-header .button-cancel, .rail .rail-verify')
  if (!waiting) {
    expectInViewport('.language-switch')
  }
  for (const selector of UNCLIPPED) {
    for (const element of document.querySelectorAll(selector)) {
      expect(element.scrollWidth, `${selector} clipped`).toBeLessThanOrEqual(element.clientWidth + 1)
    }
  }
  expect(getComputedStyle(document.body).backgroundColor).toBe(BACKGROUNDS[colorMode])
}

describe('the desktop shell', () => {
  for (const colorMode of ['light', 'dark'] as const) {
    for (const [state, seed] of Object.entries(STATES)) {
      it(`lays out ${state} in the ${colorMode} theme`, async () => {
        const { live, shell } = seedLive(seed)
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} />)
        const waiting = seed.catalog === 'waiting'
        await expect.poll(() => {
          return document.querySelector(waiting ? '.code-line' : '.monaco-editor .view-line')
        }, { timeout: 60_000 }).toBeTruthy()
        await new Promise((resolve) => {
          setTimeout(resolve, 600)
        })
        expectLayout(colorMode, waiting)
        await page.screenshot({ path: `../../build/screens/desktop-${state}-${colorMode}.png` })
        root.unmount()
        container.remove()
      })
    }
  }
})
