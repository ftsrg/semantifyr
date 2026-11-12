/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { describe, expect, it, onTestFinished } from 'vitest'
import { page, server } from 'vitest/browser'

import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import type { SharedResults } from '../lib/workspace/sharedResults'
import { ShellRoot } from '../shells/ShellRoot'
import { caseOf, finished, seedLive, type Seed } from './seededLive'

// Goal: each state of keeping work the mockup draws shows the element that tells it from the plain screen, in both themes

const BARRIER = caseOf('BarrierDownWhileTrainInCrossingViolated', 97)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 112)

const BACKGROUNDS: Readonly<Record<ResolvedColorMode, string>> = {
  light: 'rgb(241, 238, 231)',
  dark: 'rgb(30, 29, 26)',
}

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase']): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

const SHARED: SharedResults = {
  selectedCase: BARRIER.id,
  verdicts: new Map([
    [BARRIER.id, 'failed'],
    [TWO_TRAINS.id, 'passed'],
  ]),
}

const FLIPPED: SharedResults = {
  selectedCase: BARRIER.id,
  verdicts: new Map([
    [BARRIER.id, 'failed'],
    [TWO_TRAINS.id, 'failed'],
  ]),
}

function text(selector: string): string {
  return document.querySelector<HTMLElement>(selector)?.innerText ?? ''
}

function count(selector: string): number {
  return document.querySelectorAll(selector).length
}

interface ScreenState {
  seed: Seed
  act?: () => Promise<void>
  expectState: () => void
}

const STATES: Readonly<Record<string, ScreenState>> = {
  'plain': {
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'stale' }, { caseInfo: TWO_TRAINS, status: 'stale' }], 'idle') },
    expectState: () => {
      expect(count('.unsaved-mark')).toBe(0)
      expect(count('.save-split')).toBe(1)
      expect(count('.save-menu')).toBe(0)
      expect(text('[data-testid=status-message]')).not.toContain('Unsaved changes')
      expect(count('[data-testid=shared-notice]')).toBe(0)
      expect(count('[data-testid=drop-target]')).toBe(0)
    },
  },
  'unsaved': {
    seed: { unsaved: true, verification: discovered([{ caseInfo: BARRIER, status: 'stale' }, { caseInfo: TWO_TRAINS, status: 'stale' }], 'idle') },
    expectState: () => {
      expect(count('.tab-model .unsaved-mark')).toBe(1)
      expect(text('[data-testid=status-message]')).toContain('Unsaved changes. Ctrl S')
    },
  },
  'save-menu': {
    seed: { unsaved: true, verification: discovered([{ caseInfo: BARRIER, status: 'stale' }, { caseInfo: TWO_TRAINS, status: 'stale' }], 'idle') },
    act: async () => {
      document.querySelector<HTMLElement>('.save-split button[aria-label="Save options"]')?.click()
      await new Promise((resolve) => {
        setTimeout(resolve, 500)
      })
    },
    expectState: () => {
      expect(text('.save-menu')).toContain('Ctrl Shift S')
      expect(text('.save-menu')).toContain('Save as...')
      expect(text('.save-menu .save-note')).toContain('Save ')
    },
  },
  'file-over-the-editor': {
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'stale' }, { caseInfo: TWO_TRAINS, status: 'stale' }], 'idle') },
    act: async () => {
      const files = new DataTransfer()
      files.items.add(new File(['part def Dropped {}'], 'dropped.sysml'))
      document.querySelector('section.editor')?.dispatchEvent(new DragEvent('dragenter', { dataTransfer: files, bubbles: true, cancelable: true }))
    },
    expectState: () => {
      expect(text('[data-testid=drop-target]')).toContain('Drop to open the file')
    },
  },
  'share-popover': {
    seed: { verification: discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done'), selection: BARRIER.id },
    act: async () => {
      const share = [...document.querySelectorAll<HTMLElement>('.toolbar button')].find((button) => {
        return button.innerText.trim() === 'Share'
      })
      share?.click()
      await new Promise((resolve) => {
        setTimeout(resolve, 800)
      })
    },
    expectState: () => {
      expect(document.querySelector('[role=radiogroup] [role=radio][aria-checked=true]')?.textContent).toContain('With results')
      expect(document.querySelector<HTMLButtonElement>('[role=radiogroup] [role=radio]:first-child')?.disabled).toBe(false)
    },
  },
  'share-opened-running': {
    seed: {
      sharedResults: SHARED,
      selection: BARRIER.id,
      queued: { caseId: null },
      verification: discovered([{ caseInfo: BARRIER, status: 'queued' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running'),
    },
    expectState: () => {
      expect(count('.case-row.is-shared .shared-tag')).toBe(2)
      expect(text('[data-testid=shared-notice]')).toContain('replaced when your run produces its own')
    },
  },
  'share-opened-differs': {
    seed: {
      sharedResults: FLIPPED,
      selection: BARRIER.id,
      verification: discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done'),
    },
    expectState: () => {
      expect(count('.case-row .shared-tag')).toBe(0)
      expect(count('.case-row .differs-tag')).toBe(1)
      expect(text('.case-row .differs-tag')).toContain('Differs, shared: Failed')
      expect(text('[data-testid=shared-notice]')).toContain('1 of 2 results came out differently')
    },
  },
}

describe('keeping work in the desktop shell', () => {
  for (const colorMode of ['light', 'dark'] as const) {
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
        const documentRoot = document.documentElement
        expect(documentRoot.scrollWidth, 'horizontal overflow').toBeLessThanOrEqual(documentRoot.clientWidth)
        expect(getComputedStyle(document.body).backgroundColor).toBe(BACKGROUNDS[colorMode])
        await page.screenshot({ path: `../../build/screens/${server.browser}/keeping-${state}-${colorMode}.png` })
      })
    }
  }
})
