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

// Goal: a tablet gets the desktop shell, and in either orientation nothing of it overflows the viewport or clips its words

const ORIENTATIONS: Readonly<Record<string, { width: number, height: number }>> = {
  portrait: { width: 768, height: 1024 },
  landscape: { width: 1024, height: 768 },
}

const BARRIER = caseOf('BarrierDownWhileTrainInCrossingViolated', 97)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 112)

const UNCLIPPED = [
  '.toolbar-group',
  '.language-switch',
  '.results-header',
  '.counts',
  '.case-outcome',
  '.status-end',
  '.orientation-text',
]

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase'] = 'idle'): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

interface ScreenState {
  seed: Seed
  expectState: () => void
}

function count(selector: string): number {
  return document.querySelectorAll(selector).length
}

const STATES: Readonly<Record<string, ScreenState>> = {
  'first': {
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'stale' }, { caseInfo: TWO_TRAINS, status: 'stale' }]) },
    expectState: () => {
      expect(count('.orientation .orientation-text')).toBe(1)
      expect(count('.case-row.status-not-run')).toBe(2)
    },
  },
  'results': {
    seed: { verification: discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done'), selection: BARRIER.id },
    expectState: () => {
      expect(count('.case-row.status-failed')).toBe(1)
      expect(count('.case-row.status-passed')).toBe(1)
    },
  },
  // the widest toolbar: a flavour menu and the longest example name
  'oxsts': {
    seed: { flavorId: 'oxsts', exampleId: 'intermediate', verification: discovered([{ caseInfo: caseOf('GreenColorIsReachable', 40, 'file:///workspace/snippet.oxsts'), status: 'stale' }]) },
    expectState: () => {
      expect(count('.flavour-button')).toBe(1)
      expect(count('.segmented-option[data-language=oxsts][aria-pressed=true]')).toBe(1)
    },
  },
}

function expectApart(left: string, right: string): void {
  const leftBox = document.querySelector(left)!.getBoundingClientRect()
  const rightBox = document.querySelector(right)!.getBoundingClientRect()
  expect(leftBox.right, `${left} overlaps ${right}`).toBeLessThanOrEqual(rightBox.left)
}

function expectInViewport(selector: string): void {
  for (const element of document.querySelectorAll(selector)) {
    const box = element.getBoundingClientRect()
    expect(box.left, `${selector} left`).toBeGreaterThanOrEqual(0)
    expect(box.right, `${selector} right`).toBeLessThanOrEqual(window.innerWidth)
    expect(box.bottom, `${selector} bottom`).toBeLessThanOrEqual(window.innerHeight)
  }
}

describe('the desktop shell on a tablet', () => {
  for (const [orientation, size] of Object.entries(ORIENTATIONS)) {
    for (const colorMode of ['light', 'dark'] as const satisfies readonly ResolvedColorMode[]) {
      for (const [state, screen] of Object.entries(STATES)) {
        it(`lays out ${state} in ${orientation} in the ${colorMode} theme`, async () => {
          await page.viewport(size.width, size.height)
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
          await new Promise((resolve) => {
            setTimeout(resolve, 600)
          })
          expect(count('[data-testid=desktop-shell]')).toBe(1)
          for (const name of ['Open', 'Save']) {
            expect(page.getByRole('button', { name, exact: true }).elements(), `${name} named in the toolbar`).toHaveLength(1)
          }
          screen.expectState()
          const { scrollWidth, clientWidth } = document.documentElement
          expect(scrollWidth, 'horizontal overflow').toBeLessThanOrEqual(clientWidth)
          expectInViewport('.toolbar .button, .toolbar .segmented-option, .results-header .button, .statusbar')
          expectApart('.toolbar-group .example-button', '.toolbar-group-end')
          for (const selector of UNCLIPPED) {
            for (const element of document.querySelectorAll(selector)) {
              expect(element.scrollWidth, `${selector} clipped`).toBeLessThanOrEqual(element.clientWidth + 1)
            }
          }
          await page.screenshot({ path: `../../build/screens/tablet-${orientation}-${state}-${colorMode}.png` })
        })
      }
    }
  }
})
