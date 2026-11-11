/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode'
import { createRoot } from 'react-dom/client'
import { describe, expect, it, onTestFinished } from 'vitest'
import { page, server } from 'vitest/browser'

import { toVscodeDiagnostics, type ProblemEntry } from '../lib/session/problems'
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

const MODEL_OXSTS = 'package semantifyr::sysml::model\n\nclass LevelCrossing {\n}\n'

const PROBLEMS: readonly ProblemEntry[] = [
  { severity: 'error', startLine: 60, startColumn: 4, endLine: 60, endColumn: 9, message: 'missing \';\' at \'}\'', source: 'sysml' },
  { severity: 'warning', startLine: 9, startColumn: 9, endLine: 9, endColumn: 13, message: 'Item \'Lift\' is sent but never accepted.', source: 'sysml' },
]

interface ScreenState {
  seed: Seed
  // the model's diagnostics, set the way a language server sets them, so the editor fills the problems
  problems?: readonly ProblemEntry[]
  // brings the screen to its state once it is drawn
  act?: () => Promise<void>
  // the element that tells this state apart from the first screen
  expectState: () => void
}

// the editor draws spaces as no-break spaces
function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/\u00a0/g, ' ')
}

function count(selector: string): number {
  return document.querySelectorAll(selector).length
}

const STATES: Readonly<Record<string, ScreenState>> = {
  'first': {
    seed: { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]) },
    expectState: () => {
      expect(text('.orientation-text')).toContain('Press Verify all')
      expect(count('.case-row.status-not-run')).toBe(2)
      expect(count('.results-header .button-primary')).toBe(1)
    },
  },
  'initializing': {
    seed: { initializingAt: 'cases' },
    expectState: () => {
      expect([...document.querySelectorAll<HTMLElement>('.initialization-step.is-current')].map((step) => {
        return step.innerText
      }).join('\n')).toMatch(/Initializing the SysML v2 language server[\s\S]*Finding the verification cases/)
      expect(count('.initialization-pressed')).toBe(0)
    },
  },
  'initializing-pressed': {
    seed: { initializingAt: 'cases', queued: { caseId: null } },
    expectState: () => {
      expect(text('.initialization-pressed')).toContain('Verify all is pressed')
      expect(count('.results-header .button-cancel')).toBe(1)
      expect(text('[data-testid=status-message]')).toBe('Verify all waits: finding the verification cases')
    },
  },
  'cases-while-initializing': {
    seed: { initializingAt: 'initialize', verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]) },
    expectState: () => {
      expect(count('.case-row.status-not-run')).toBe(2)
      expect(count('.initialization')).toBe(0)
      expect(text('[data-testid=status-message]')).toBe('Initializing the SysML v2 language server')
      expect(count('.connection.is-busy')).toBe(1)
    },
  },
  'case-queued': {
    seed: { initializingAt: 'initialize', verification: discovered([{ caseInfo: BARRIER, status: 'queued' }, notRun(TWO_TRAINS)]), queued: { caseId: BARRIER.id } },
    expectState: () => {
      expect(count(`.case-row.status-queued[data-case="${BARRIER.id}"]`)).toBe(1)
      expect(count('.case-row.status-not-run')).toBe(1)
      expect(count('.results-header .button-cancel')).toBe(1)
      expect(text('[data-testid=status-message]')).toBe(`Verify ${BARRIER.id} waits: initializing the SysML v2 language server`)
    },
  },
  'initialization-timed-out': {
    seed: { initializingAt: 'initialize', initializationFailure: 'Initializing the SysML v2 language server did not end within 60 s.', verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]) },
    expectState: () => {
      expect(count('.case-row.status-not-run')).toBe(2)
      expect(text('[data-testid=status-message]')).toBe('Initializing the SysML v2 language server did not end within 60 s.')
    },
  },
  'running': {
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'running', serverState: 'Running' }, { caseInfo: TWO_TRAINS, status: 'running', serverState: 'Queued' }], 'running') },
    expectState: () => {
      expect(count('.results-header .button-cancel')).toBe(1)
      expect(count(`.case-row.status-running[data-case="${BARRIER.id}"]`)).toBe(1)
      expect(count(`.case-row.status-queued[data-case="${TWO_TRAINS.id}"]`)).toBe(1)
      expect(text('[data-testid=status-message]')).toBe(`Verifying ${BARRIER.id}, 0 of 2 done`)
    },
  },
  'server-busy': {
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'running', serverState: 'Queued' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running') },
    expectState: () => {
      expect(count('.case-row.status-queued')).toBe(2)
      expect(count('.case-row.status-running')).toBe(0)
      expect(text('[data-testid=status-message]')).toBe(`The server is busy with other verifications: ${BARRIER.id} waits`)
    },
  },
  'results': {
    // the run ends as the screen opens, so the status line still closes it
    get seed(): Seed {
      return { verification: RESULTS, selection: BARRIER.id, timing: { key: 'sysml#level-crossing', caseId: null, startedAt: Date.now() - 3700, finishedAt: Date.now() } }
    },
    expectState: () => {
      expect(count('.case-row.status-failed')).toBe(1)
      expect(count('.case-row.status-passed')).toBe(1)
      expect(text('.trace-title')).toContain(BARRIER.id)
      expect(text('[data-testid=status-message]')).toBe('Done in 2.9 s: 1 failed, 1 passed')
    },
  },
  'outdated': {
    seed: { verification: OUTDATED, selection: BARRIER.id },
    expectState: () => {
      expect(count('.case-row.is-outdated .outdated-tag')).toBe(2)
      expect(text('[data-testid=status-message]')).toContain('outdated')
    },
  },
  'address-unknown-example': {
    seed: { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]), address: { kind: 'example', requested: 'level-crosing', flavorId: 'sysml', exampleId: 'level-crossing' } },
    expectState: () => {
      expect(text('.orientation.is-error')).toContain('The address names the example "level-crosing"')
    },
  },
  'connection-terminated': {
    seed: { verification: RESULTS, connection: connectionLost(CONNECTED, 'failed', 'Session terminated', { terminated: true }) },
    expectState: () => {
      expect(text('.connection')).toContain('terminated')
      expect(text('[data-testid=status-message] .button-link')).toBe('Reconnect')
    },
  },
  'server-unreachable': {
    seed: { catalog: 'waiting' },
    expectState: () => {
      expect(text('.connection')).toContain('retrying')
      expect(text('.initialization-step.is-failed')).toContain('Connecting')
      expect(count('.code-line')).toBeGreaterThan(10)
    },
  },
  'collapsed': {
    seed: { verification: RESULTS, settings: { resultsColumnCollapsed: true } },
    expectState: () => {
      expect(count('.rail')).toBe(1)
      expect(count('.results')).toBe(0)
      expect(count('.rail .model-oxsts-button')).toBe(1)
    },
  },
  'tab-model-oxsts': {
    seed: { verification: RESULTS, modelOxsts: MODEL_OXSTS, tab: modelOxstsTabOf('level-crossing.sysml', MODEL_OXSTS) },
    expectState: () => {
      expect(text('.tab.is-readonly[aria-selected=true]')).toContain('level-crossing.oxsts')
      expect(text('.readonly-banner')).toContain('compiles to')
      expect(text('.monaco-editor .view-lines')).toContain('class LevelCrossing')
    },
  },
  'failure-problems': {
    seed: { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]), verifyBlocked: true },
    problems: PROBLEMS,
    act: async () => {
      await expect.poll(() => {
        return text('.problem-counts')
      }).toContain('1')
      document.querySelector<HTMLElement>('.problem-counts')?.click()
      await expect.poll(() => {
        return count('.problems-popover .problem-item')
      }).toBe(2)
    },
    expectState: () => {
      expect(text('.problem-counts').replace(/\s+/g, ' ')).toBe('1 1')
      expect(text('[data-testid=status-message]')).toContain('Verify all did not run: the model has 1 error')
      expect(count('.case-row.status-running, .case-row.status-queued')).toBe(0)
    },
  },
  'about': {
    seed: { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]) },
    act: async () => {
      const about = [...document.querySelectorAll<HTMLElement>('.toolbar .button')].find((button) => {
        return button.innerText.trim() === 'About'
      })
      about!.click()
      await expect.poll(() => {
        return count('.dialog')
      }).toBe(1)
    },
    expectState: () => {
      const about = text('.dialog')
      expect(about).toContain('research prototype')
      expect(about).toContain('deletes the folder when the session ends')
      expect(about).toContain('network address of each session')
      expect(about).toContain('the name and verdict of each case it ran')
      expect(about).toContain('The text of your model is never logged')
      expect(about).not.toContain('No model and no result is kept')
      expect(about).not.toContain('collects no personal data')
      expect(about).not.toContain('only while it verifies')
    },
  },
  'gamma': {
    seed: { flavorId: 'gamma', verification: discovered([notRun(caseOf('BarrierDownWhileTrainInCrossing', 90, 'file:///workspace/snippet.gamma'))]) },
    expectState: () => {
      expect(count('.segmented-option[data-language=gamma][aria-pressed=true]')).toBe(1)
      expect(count('.tab-model .language-mark[data-language=gamma]')).toBe(1)
      expect(text('.results-header .model-oxsts-button')).toBe('OXSTS')
    },
  },
  'oxsts': {
    seed: { flavorId: 'oxsts', exampleId: 'level-crossing', verification: discovered([notRun(caseOf('BarrierDownWhileTrainInCrossing', 90, 'file:///workspace/snippet.oxsts'))]) },
    expectState: () => {
      expect(count('.segmented-option[data-language=oxsts][aria-pressed=true]')).toBe(1)
      expect(count('.tab-model .language-mark[data-language=oxsts]')).toBe(1)
      expect(text('.flavour-button')).toContain('Plain')
      expect(count('.model-oxsts-button')).toBe(0)
    },
  },
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
  // bracket pairs take the tokens' colours, none of their own
  expect(count('.monaco-editor [class*=bracket-highlighting]')).toBe(0)
}

describe('the desktop shell', () => {
  for (const colorMode of ['light', 'dark'] as const) {
    for (const [state, screen] of Object.entries(STATES)) {
      it(`lays out ${state} in the ${colorMode} theme`, async () => {
        const { seed } = screen
        const { live, shell } = seedLive(seed)
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        let diagnostics: vscode.DiagnosticCollection | null = null
        onTestFinished(() => {
          diagnostics?.dispose()
          root.unmount()
          container.remove()
        })
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} />)
        const waiting = seed.catalog === 'waiting'
        await expect.poll(() => {
          return document.querySelector(waiting ? '.code-line' : '.monaco-editor .view-line')
        }, { timeout: 60_000 }).toBeTruthy()
        if (screen.problems) {
          diagnostics = vscode.languages.createDiagnosticCollection('screen')
          diagnostics.set(vscode.Uri.parse('file:///workspace/snippet.sysml'), toVscodeDiagnostics(screen.problems))
        }
        await screen.act?.()
        await new Promise((resolve) => {
          setTimeout(resolve, 600)
        })
        screen.expectState()
        expectLayout(colorMode, waiting)
        await page.screenshot({ path: `../../build/screens/${server.browser}/desktop-${state}-${colorMode}.png` })
      })
    }
  }
})
