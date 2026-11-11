/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { beforeAll, describe, expect, it, onTestFinished } from 'vitest'
import { page, server } from 'vitest/browser'

import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { ShellRoot } from '../shells/ShellRoot'
import { CaseTrace } from '../trace/CaseTrace'
import { caseOf, finished, seedLive, type Seed, type Seeded } from './seededLive'
import { caseState, LANGUAGES, seedWithTrace } from './traces'

// Goal: both embed views lay out at the width the mockup frames them, in both themes, with the real editor

const WIDTH = 760

const BARRIER = caseOf('BarrierDownWhileTrainInCrossingViolated', 97)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 112)

const SYSML = LANGUAGES.sysml!
const TRACED = caseState(SYSML, { validated: true, recorded: true })

const EXCERPT = `part def Track {
    port sensors : ~Sensors;
    port controller : TrackReports;

    exhibit state Behaviour {
        transition first start then Free;

        state Free;
        state Approaching;

        transition first Free accept : ApproachSignal via sensors then Approaching;
    }
}
`

function notRun(caseInfo: typeof BARRIER): VerificationCaseState {
  return { caseInfo, status: 'stale' }
}

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase'] = 'idle'): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

const RESULTS = discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done')

interface ScreenState {
  view: 'embed' | 'edit-only'
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

const STATES: Readonly<Record<string, ScreenState>> = {
  'rest': {
    view: 'embed',
    seed: { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]) },
    expectState: () => {
      expect(count('.strip-bar .button-primary')).toBe(1)
      expect(count('.embed-case.status-not-run')).toBe(2)
      expect(text('[data-testid=status-message]')).toContain('Ready: 2 verification cases')
      expect(text('.open-live')).toContain('Open in Live')
    },
  },
  'cases-while-initializing': {
    view: 'embed',
    seed: { initializingAt: 'initialize', verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]), queued: { caseId: null } },
    expectState: () => {
      expect(count('.embed-case.status-not-run')).toBe(2)
      expect(count('.strip-bar .button-cancel')).toBe(1)
      expect(text('[data-testid=status-message]')).toBe('Verify all waits: initializing the SysML v2 language server')
    },
  },
  'initialization-timed-out': {
    view: 'embed',
    seed: { initializingAt: 'initialize', initializationFailure: 'Initializing the SysML v2 language server did not end within 60 s.', verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]) },
    expectState: () => {
      expect(count('.embed-case.status-not-run')).toBe(2)
      expect(text('[data-testid=status-message]')).toBe('Initializing the SysML v2 language server did not end within 60 s.')
    },
  },
  'running': {
    view: 'embed',
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'running' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running') },
    expectState: () => {
      expect(count('.strip-bar .button-cancel')).toBe(1)
      expect(count('.embed-case.status-running')).toBe(1)
      expect(count('.embed-case.status-queued')).toBe(1)
      expect(text('[data-testid=status-message]')).toContain('Verifying')
    },
  },
  'results': {
    view: 'embed',
    seed: { verification: RESULTS },
    expectState: () => {
      expect(count('.embed-case.status-failed.has-trace')).toBe(1)
      expect(count('.embed-case.status-passed')).toBe(1)
      expect(count('.embed-trace')).toBe(0)
    },
  },
  'trace': {
    view: 'embed',
    seed: () => {
      return seedWithTrace(SYSML, TRACED)
    },
    expectState: () => {
      expect(count('.embed-case.is-open')).toBe(1)
      expect(text('.embed-trace .trace-views [aria-pressed=true]')).toBe('SysML trace')
      expect(text('.embed-trace [data-testid=run-details]')).toContain('decided by Theta')
      // the trace text is as tall as it is, every line drawn, and wraps instead of scrolling sideways
      const numbered = [...document.querySelectorAll('.embed-trace .trace-editor .line-numbers')].map((element) => {
        return element.textContent.trim()
      }).filter((number) => {
        return number !== ''
      })
      expect(numbered).toEqual(Array.from({ length: 14 }, (_, index) => {
        return String(index + 1)
      }))
      expect(document.querySelector('.embed-trace .trace-editor .view-lines')!.scrollWidth).toBeLessThanOrEqual(document.querySelector('.embed-trace .trace-editor')!.clientWidth)
    },
  },
  'trace-steps': {
    view: 'embed',
    seed: () => {
      return seedWithTrace(SYSML, TRACED)
    },
    act: async () => {
      await expect.poll(() => {
        return document.querySelector('.embed-trace .trace-views [data-view=steps]')
      }).toBeTruthy()
      document.querySelector<HTMLElement>('.embed-trace .trace-views [data-view=steps]')!.click()
    },
    expectState: () => {
      expect(count('.embed-trace [data-testid=trace-steps] .step')).toBe(4)
    },
  },
  'trace-calls': {
    view: 'embed',
    seed: () => {
      return seedWithTrace(SYSML, TRACED)
    },
    act: async () => {
      await expect.poll(() => {
        return document.querySelector('.embed-trace .trace-views [data-view=calls]')
      }).toBeTruthy()
      document.querySelector<HTMLElement>('.embed-trace .trace-views [data-view=calls]')!.click()
    },
    expectState: () => {
      expect(count('.embed-trace [data-testid=trace-calls] .call')).toBe(10)
    },
  },
  'address-error': {
    view: 'embed',
    seed: { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]), address: { kind: 'example', requested: 'level-crosing', flavorId: 'sysml', exampleId: 'level-crossing' } },
    expectState: () => {
      expect(text('.embed-address-error')).toContain('The address names the example "level-crosing"')
      expect(text('.embed-address-error')).toContain('Opened Level crossing in its place')
    },
  },
  'server-unreachable': {
    view: 'embed',
    seed: { catalog: 'waiting' },
    expectState: () => {
      expect(text('[data-testid=status-message]')).toContain('The server is not answering')
      expect(text('.embed-initializing')).toContain('not answering')
      expect(count('.embed-editor .code-line')).toBeGreaterThan(10)
    },
  },
  'edit-only': {
    view: 'edit-only',
    seed: { text: EXCERPT },
    expectState: () => {
      expect(count('.embed.is-edit-only')).toBe(1)
      expect(count('.embed-strip')).toBe(0)
      expect(text('.monaco-editor .view-lines')).toContain('part def Track')
      const editor = document.querySelector('.embed-editor')!.getBoundingClientRect().height
      expect(editor, 'the editor fits its text').toBeLessThan(400)
      expect(editor, 'the editor shows every line').toBeGreaterThanOrEqual(13 * 20)
    },
  },
}

describe('the embed shell', () => {
  beforeAll(async () => {
    await page.viewport(WIDTH, 1200)
  })

  for (const colorMode of ['light', 'dark'] as const satisfies readonly ResolvedColorMode[]) {
    for (const [state, screen] of Object.entries(STATES)) {
      it(`lays out ${state} in the ${colorMode} theme`, async () => {
        const address = `${window.location.pathname}${window.location.search}`
        window.history.replaceState(null, '', `${window.location.pathname}?view=${screen.view}`)
        const { live, shell } = typeof screen.seed === 'function' ? screen.seed() : seedLive(screen.seed)
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        onTestFinished(() => {
          root.unmount()
          container.remove()
          window.history.replaceState(null, '', address)
        })
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} trace={CaseTrace} />)
        const waiting = typeof screen.seed !== 'function' && screen.seed.catalog === 'waiting'
        await expect.poll(() => {
          return document.querySelector(waiting ? '.code-line' : '.monaco-editor .view-line')
        }, { timeout: 60_000 }).toBeTruthy()
        await screen.act?.()
        await new Promise((resolve) => {
          setTimeout(resolve, 600)
        })
        screen.expectState()

        const embed = document.querySelector<HTMLElement>('[data-testid=embed-shell]')!
        const box = embed.getBoundingClientRect()
        expect(document.documentElement.scrollWidth, 'horizontal overflow').toBeLessThanOrEqual(document.documentElement.clientWidth)
        expect(box.right).toBeLessThanOrEqual(window.innerWidth)
        for (const selector of ['.strip-bar', '.embed-case-verdict', '.embed-case-end']) {
          for (const element of document.querySelectorAll(selector)) {
            expect(element.scrollWidth, `${selector} clipped`).toBeLessThanOrEqual(element.clientWidth + 1)
          }
        }
        expect(getComputedStyle(document.body).backgroundColor, 'the page shows around the frame').toBe('rgba(0, 0, 0, 0)')
        expect(getComputedStyle(embed).backgroundColor).not.toBe('rgba(0, 0, 0, 0)')
        await page.screenshot({ path: `../../build/screens/${server.browser}/embed-${state}-${colorMode}.png`, element: embed })
      })
    }
  }
})
