/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode'
import { createRoot } from 'react-dom/client'
import { describe, expect, it, onTestFinished } from 'vitest'
import { page, userEvent } from 'vitest/browser'

import { toVscodeDiagnostics } from '../lib/session/problems'
import { connectionLost, INITIAL_CONNECTION } from '../lib/stores/connection'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { ShellRoot } from '../shells/ShellRoot'
import { caseOf, finished, seedLive, type Seed } from './seededLive'

// Goal: what the desktop shows while a case is run again, and what reaches the page's handlers from inside the editor, in both themes

const BARRIER = caseOf('BarrierDownWhileTrainInCrossingViolated', 97)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 112)

const saves: string[] = []

// the page asks the browser where to save; the test answers as the visitor would
Object.assign(window, {
  showSaveFilePicker: async (options: { suggestedName: string }) => {
    saves.push(options.suggestedName)
    return {
      name: options.suggestedName,
      createWritable: async () => {
        return {
          write: async () => {},
          close: async () => {},
        }
      },
    }
  },
})

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase']): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/\u00a0/g, ' ')
}

function rowText(caseId: string): string {
  return text(`.case-row[data-case="${caseId}"]`)
}

interface ScreenState {
  seed: Seed
  act?: () => Promise<void>
  expectState: () => void
}

const SHARED_VERDICTS = {
  selectedCase: BARRIER.id,
  verdicts: new Map([
    [BARRIER.id, 'failed' as const],
    [TWO_TRAINS.id, 'passed' as const],
  ]),
}

const { witnessValidation: _validated, ...UNVALIDATED_BARRIER } = finished(BARRIER, 'failed', 2.9)

const STATES: Readonly<Record<string, ScreenState>> = {
  'validating': {
    seed: {
      selection: BARRIER.id,
      verification: discovered([{ ...UNVALIDATED_BARRIER, validating: true }, finished(TWO_TRAINS, 'passed', 0.8)], 'done'),
    },
    expectState: () => {
      expect(document.querySelector(`.case-row[data-case="${BARRIER.id}"] .validation-mark.is-validating`)).not.toBeNull()
      expect(document.querySelector(`.case-row[data-case="${BARRIER.id}"] [aria-label^="Validate"]`)).toBeNull()
      expect(document.querySelector(`.case-row[data-case="${TWO_TRAINS.id}"] [aria-label^="Validate again"]`)).not.toBeNull()
    },
  },
  'flavour-and-one-error': {
    seed: { flavorId: 'oxsts-with-gamma-library', verification: discovered([], 'idle') },
    act: async () => {
      const diagnostics = vscode.languages.createDiagnosticCollection('fixes')
      diagnostics.set(vscode.Uri.parse('file:///workspace/snippet.oxsts'), toVscodeDiagnostics([
        { severity: 'error', startLine: 0, startColumn: 0, endLine: 0, endColumn: 3, message: 'broken', source: 'oxsts' },
      ]))
      onTestFinished(() => {
        diagnostics.dispose()
      })
    },
    expectState: () => {
      expect(text('.status-language')).toBe('OXSTS, with the Gamma library')
      expect(document.querySelector('.problem-counts')?.getAttribute('aria-label')).toBe('Problems in the model: 1 error, 0 warnings')
    },
  },
  'opened-from-a-link-and-verified': {
    seed: {
      sharedResults: SHARED_VERDICTS,
      selection: BARRIER.id,
      verification: discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done'),
    },
    expectState: () => {
      expect(text('.orientation-text')).toContain('Level crossing.')
      expect(text('.orientation-text')).not.toContain('Press')
    },
  },
  'server-not-answering-gamma': {
    seed: { catalog: 'waiting', location: '/gamma/crossroads' },
    expectState: () => {
      expect(document.querySelector('[data-testid=waiting-model]')?.getAttribute('data-language')).toBe('gamma')
      expect(text('[data-testid=waiting-model]')).toContain('package')
      expect(text('.language-switch [aria-pressed=true]')).toBe('Gamma')
      expect(text('.example-button')).toBe('Crossroads')
    },
  },
  'session-refused': {
    seed: {
      initializingAt: 'server',
      connection: connectionLost(INITIAL_CONNECTION, 'connecting', 'Session limit reached', { reconnecting: true, refused: true, retryAt: Date.now() + 300_000 }),
    },
    expectState: () => {
      expect(text('[data-testid=status-message]')).toMatch(/The server refused the session: it is at capacity\. Retrying in \d+ s; the wait grows with each refusal\./)
      expect(text('.connection-reason')).toBe('refused, retrying')
      expect(text('.initialization-note')).toContain('The server refused the session')
    },
  },
  'connection-lost': {
    seed: {
      verification: discovered([finished(BARRIER, 'failed', 2.9), { caseInfo: TWO_TRAINS, status: 'stale', message: 'Verification cancelled' }], 'cancelled'),
      connection: connectionLost(INITIAL_CONNECTION, 'connecting', 'Connection lost', { reconnecting: true, retryAt: Date.now() + 300_000 }),
    },
    expectState: () => {
      expect(text('[data-testid=status-message]')).toMatch(/Connection lost\. Reconnecting in \d+ s\. Reconnect now/)
      expect(rowText(TWO_TRAINS.id)).toContain('cancelled')
    },
  },
  'run-again': {
    seed: {
      selection: BARRIER.id,
      verification: discovered([
        { ...finished(BARRIER, 'failed', 2.9), status: 'running' },
        { ...finished(TWO_TRAINS, 'passed', 0.8), status: 'queued' },
      ], 'running'),
    },
    expectState: () => {
      expect(rowText(BARRIER.id)).toContain('Failed')
      expect(rowText(BARRIER.id)).toContain('Running')
      expect(rowText(TWO_TRAINS.id)).toContain('Passed')
      expect(rowText(TWO_TRAINS.id)).toContain('Queued')
      expect(document.querySelector('.count.status-failed')?.textContent).toBe('1')
      expect(document.querySelector('.count.status-passed')?.textContent).toBe('1')
      expect(text('.trace .trace-title')).toContain(BARRIER.id)
    },
  },
  'save-from-the-editor': {
    seed: { unsaved: true, verification: discovered([{ caseInfo: BARRIER, status: 'stale' }, { caseInfo: TWO_TRAINS, status: 'stale' }], 'idle') },
    act: async () => {
      saves.length = 0
      await userEvent.click(document.querySelector<HTMLElement>('.view-lines')!)
      await userEvent.keyboard('{Control>}s{/Control}')
      await userEvent.keyboard('{Control>}{Shift>}s{/Shift}{/Control}')
    },
    expectState: () => {
      expect(saves).toEqual(['level-crossing.sysml', 'level-crossing.sysml'])
    },
  },
}

describe('the fixes of the desktop shell', () => {
  for (const colorMode of ['light', 'dark'] as const satisfies readonly ResolvedColorMode[]) {
    for (const [state, screen] of Object.entries(STATES)) {
      it(`shows ${state} in the ${colorMode} theme`, async () => {
        const { live, shell } = seedLive(screen.seed)
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        onTestFinished(() => {
          root.unmount()
          container.remove()
        })
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} />)
        await expect.poll(() => {
          return document.querySelector('.monaco-editor .view-line, [data-testid=waiting-model]')
        }, { timeout: 60_000 }).toBeTruthy()
        await screen.act?.()
        await new Promise((resolve) => {
          setTimeout(resolve, 600)
        })
        screen.expectState()
        await page.screenshot({ path: `../../build/screens/fixes-${state}-${colorMode}.png` })
      })
    }
  }
})
