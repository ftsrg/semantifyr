/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { describe, expect, it, onTestFinished } from 'vitest'
import { page } from 'vitest/browser'

import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { ShellRoot } from '../shells/ShellRoot'
import { caseOf, finished, seedLive, type Seed } from './seededLive'

// Goal: what the desktop shows while a case is run again, in both themes

const BARRIER = caseOf('BarrierDownWhileTrainInCrossingViolated', 97)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 112)

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase']): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/ /g, ' ')
}

function rowText(caseId: string): string {
  return text(`.case-row[data-case="${caseId}"]`)
}

interface ScreenState {
  seed: Seed
  act?: () => Promise<void>
  expectState: () => void
}

const STATES: Readonly<Record<string, ScreenState>> = {
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
          return document.querySelector('.monaco-editor .view-line')
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
