/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { createRoot } from 'react-dom/client'
import { beforeAll, describe, expect, it, onTestFinished, vi } from 'vitest'
import { page } from 'vitest/browser'

import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { ShellRoot } from '../shells/ShellRoot'
import type { TraceSlotProps } from '../shells/traceSlot'
import { caseOf, finished, seedLive, type Seed } from './seededLive'

// Goal: both embed views lay out at the width the mockup frames them, in both themes, with the real editor,
// and the frame's page is told the height the embed takes, the trace's included

const WIDTH = 760

const BARRIER = caseOf('BarrierDownWhileTrainInCrossingViolated', 97)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 112)

const STUB_HEIGHT = 240

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

function TraceStub({ caseState }: TraceSlotProps): React.JSX.Element {
  return <div className="trace-stub" style={{ height: STUB_HEIGHT }}>{`Trace of ${caseState.caseInfo.id}`}</div>
}

function notRun(caseInfo: typeof BARRIER): VerificationCaseState {
  return { caseInfo, status: 'stale' }
}

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase'] = 'idle'): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

const RESULTS = discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done')

interface ScreenState {
  embed: string
  seed: Seed
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
    embed: '1',
    seed: { verification: discovered([notRun(BARRIER), notRun(TWO_TRAINS)]) },
    expectState: () => {
      expect(count('.strip-bar .button-primary')).toBe(1)
      expect(count('.embed-case.status-not-run')).toBe(2)
      expect(text('[data-testid=status-message]')).toContain('Ready: 2 verification cases')
      expect(text('.open-live')).toContain('Open in Live')
    },
  },
  'running': {
    embed: '1',
    seed: { verification: discovered([{ caseInfo: BARRIER, status: 'running' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running') },
    expectState: () => {
      expect(count('.strip-bar .button-cancel')).toBe(1)
      expect(count('.embed-case.status-running')).toBe(1)
      expect(count('.embed-case.status-queued')).toBe(1)
      expect(text('[data-testid=status-message]')).toContain('Verifying')
    },
  },
  'results': {
    embed: '1',
    seed: { verification: RESULTS },
    expectState: () => {
      expect(count('.embed-case.status-failed.has-trace')).toBe(1)
      expect(count('.embed-case.status-passed')).toBe(1)
      expect(count('.embed-trace')).toBe(0)
    },
  },
  'trace': {
    embed: '1',
    seed: { verification: RESULTS, selection: BARRIER.id },
    expectState: () => {
      expect(count('.embed-case.is-open')).toBe(1)
      expect(text('.embed-trace .trace-stub')).toBe(`Trace of ${BARRIER.id}`)
    },
  },
  'server-unreachable': {
    embed: '1',
    seed: { catalog: 'waiting' },
    expectState: () => {
      expect(text('[data-testid=status-message]')).toContain('The server is not answering')
      expect(text('.embed-initializing')).toContain('not answering')
      expect(count('.embed-editor .code-line')).toBeGreaterThan(10)
    },
  },
  'edit-only': {
    embed: 'edit',
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

function lastReportedHeight(posted: { mock: { calls: unknown[][] } }): number | null {
  const heights = posted.mock.calls.flatMap(([message]) => {
    const height = (message as { type?: string, height?: number }).type === 'semantifyr-live:height' ? (message as { height: number }).height : undefined
    return height === undefined ? [] : [height]
  })
  return heights.at(-1) ?? null
}

describe('the embed shell', () => {
  beforeAll(async () => {
    await page.viewport(WIDTH, 1200)
  })

  for (const colorMode of ['light', 'dark'] as const satisfies readonly ResolvedColorMode[]) {
    for (const [state, screen] of Object.entries(STATES)) {
      it(`lays out ${state} in the ${colorMode} theme and reports its height`, async () => {
        const address = `${window.location.pathname}${window.location.search}`
        window.history.replaceState(null, '', `${window.location.pathname}?embed=${screen.embed}`)
        expect(window.parent, 'the tests run in a frame').not.toBe(window)
        const posted = vi.spyOn(window.parent, 'postMessage')
        const { live, shell } = seedLive(screen.seed)
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        onTestFinished(() => {
          root.unmount()
          container.remove()
          posted.mockRestore()
          window.history.replaceState(null, '', address)
        })
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} trace={TraceStub} />)
        const waiting = screen.seed.catalog === 'waiting'
        await expect.poll(() => {
          return document.querySelector(waiting ? '.code-line' : '.monaco-editor .view-line')
        }, { timeout: 60_000 }).toBeTruthy()
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
        await expect.poll(() => {
          return lastReportedHeight(posted)
        }).toBe(Math.ceil(embed.getBoundingClientRect().height))
        const stub = document.querySelector('.trace-stub')
        if (stub) {
          expect(lastReportedHeight(posted)!, 'the trace grows the frame').toBeGreaterThanOrEqual(stub.getBoundingClientRect().bottom - box.top)
        }
        await page.screenshot({ path: `../../build/screens/embed-${state}-${colorMode}.png`, element: embed })
      })
    }
  }
})
