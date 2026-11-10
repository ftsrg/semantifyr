/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { afterAll, describe, expect, it, onTestFinished } from 'vitest'
import { page } from 'vitest/browser'

import { initializeMonaco } from '../lib/session/monaco'
import { LiveContext } from '../lib/workspace/liveContext'
import { ShellRoot } from '../shells/ShellRoot'
import { CaseTrace } from '../trace/CaseTrace'
import { caseState, LANGUAGES, seedWithTrace } from './traces'

import '../ui/tokens.css'
import '../ui/components.css'
import '../ui/base.css'

// Goal: each view of the trace in each language shows what tells it apart, inside the desktop column and at phone
// width in both themes; lines wrap, the diagram scrolls sideways, and only the views' own scrollers scroll

function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/\u00a0/g, ' ')
}

function count(selector: string): number {
  return document.querySelectorAll(selector).length
}

async function chooseView(id: string): Promise<void> {
  document.querySelector<HTMLElement>(`.trace-views [data-view=${id}]`)?.click()
  await expect.poll(() => {
    return count(`.trace-views [data-view=${id}][aria-pressed=true]`)
  }).toBe(1)
}

async function editorShows(expected: string): Promise<void> {
  await expect.poll(() => {
    return text('.case-trace .trace-editor .view-lines')
  }, { timeout: 30_000 }).toContain(expected)
}

interface TraceState {
  language: string
  validated?: boolean
  recorded?: boolean
  view: string
  act?: () => Promise<void>
  expectState: () => Promise<void> | void
}

const STATES_BY_NAME: Readonly<Record<string, TraceState>> = {
  'sysml-text': {
    language: 'sysml',
    view: 'own',
    expectState: async () => {
      await editorShows('part def BarrierDownWhileTrainInCrossingViolatedTrace')
      expect(text('.case-trace .trace-frame-bar')).toContain('BarrierDownWhileTrainInCrossingViolatedTrace.sysml')
    },
  },
  'sysml-steps': {
    language: 'sysml',
    view: 'steps',
    expectState: () => {
      expect(count('.steps > .step')).toBe(4)
      expect(count('.step.is-final.status-failed')).toBe(1)
    },
  },
  'sysml-steps-expanded': {
    language: 'sysml',
    view: 'steps',
    act: async () => {
      document.querySelectorAll<HTMLElement>('.step-toggle')[1]?.click()
      await expect.poll(() => {
        return count('.step.is-open .step-variable')
      }).toBe(6)
    },
    expectState: () => {
      expect(count('.step.is-open .step-variable.is-changed')).toBe(2)
      expect(text('.step.is-open .step-variable.is-changed')).toContain('TrainPresent')
    },
  },
  'sysml-calls': {
    language: 'sysml',
    view: 'calls',
    expectState: () => {
      expect(count('.calls-head')).toBe(6)
      expect(count('.call')).toBe(6)
      expect(count('.call-arrow')).toBe(2)
      expect(text('.calls-head')).toContain('UnnamedTransitionUsage1')
      expect(document.querySelector('.calls-head')?.getAttribute('title')).toBe('root.system.trackB.Behaviour.UnnamedTransitionUsage1')
    },
  },
  'sysml-witness': {
    language: 'sysml',
    view: 'witness',
    expectState: async () => {
      await editorShows('witness of example::levelcrossing')
      expect(text('.case-trace .trace-frame-bar')).toContain('read-only')
    },
  },
  'gamma-text': {
    language: 'gamma',
    view: 'own',
    expectState: async () => {
      await editorShows('trace BarrierDownWhileTrainInCrossingTrace')
      expect(text('.case-trace .trace-frame-bar')).toContain('BarrierDownWhileTrainInCrossing.trace.gamma')
    },
  },
  'gamma-steps': {
    language: 'gamma',
    view: 'steps',
    expectState: () => {
      expect(text('.trace-views [aria-pressed=true]')).toBe('OXSTS steps')
      expect(count('.step.is-final.status-failed')).toBe(1)
    },
  },
  'gamma-calls': {
    language: 'gamma',
    view: 'calls',
    expectState: () => {
      expect(count('.call-arrow')).toBe(2)
    },
  },
  'gamma-witness': {
    language: 'gamma',
    view: 'witness',
    expectState: async () => {
      await editorShows('witness of example::levelcrossing')
      expect(text('.case-trace .trace-frame-bar')).toContain('BarrierDownWhileTrainInCrossing.witness.oxsts')
    },
  },
  'oxsts-steps': {
    language: 'oxsts',
    view: 'steps',
    expectState: () => {
      expect(text('.trace-views [aria-pressed=true]')).toBe('Steps')
      expect(text('.steps > .step:nth-child(2)')).toContain('Free')
      expect(text('.steps > .step:nth-child(2) .step-from')).toContain('Free')
    },
  },
  'oxsts-calls': {
    language: 'oxsts',
    view: 'calls',
    expectState: () => {
      expect(count('.call')).toBe(6)
    },
  },
  'oxsts-witness': {
    language: 'oxsts',
    view: 'witness',
    expectState: async () => {
      await editorShows('contains root')
    },
  },
  'oxsts-no-calls': {
    language: 'oxsts',
    recorded: false,
    view: 'calls',
    expectState: () => {
      expect(text('[data-testid=trace-no-calls]')).toContain('annotated with @Trace')
      expect(count('.calls')).toBe(0)
    },
  },
  'run-details-validated': {
    language: 'sysml',
    validated: true,
    view: 'own',
    expectState: () => {
      expect(text('[data-testid=run-details]')).toContain('2.9 s, Automatic, decided by Theta')
      expect(text('[data-testid=run-details]')).toContain('validated, 0.6 s, Automatic, decided by Spin')
    },
  },
  'run-details-not-validated': {
    language: 'sysml',
    view: 'own',
    expectState: () => {
      expect(text('[data-testid=run-details]')).toContain('decided by Theta')
      expect(text('[data-testid=run-details]')).toMatch(/Validation\s*not run/)
    },
  },
}

const PHONE_STATES = [
  'sysml-text',
  'sysml-steps-expanded',
  'sysml-calls',
  'sysml-witness',
]

function expectLayout(): void {
  const root = document.documentElement
  expect(root.scrollWidth, 'horizontal overflow').toBeLessThanOrEqual(root.clientWidth)
  const trace = document.querySelector('.case-trace')!.getBoundingClientRect()
  expect(trace.right, 'trace right').toBeLessThanOrEqual(window.innerWidth)
  expect(trace.bottom, 'trace bottom').toBeLessThanOrEqual(window.innerHeight)
  for (const selector of ['.step-item', '.step-start-line', '.step-variable', '.run-line']) {
    for (const element of document.querySelectorAll(selector)) {
      expect(element.scrollWidth, `${selector} cut`).toBeLessThanOrEqual(element.clientWidth + 1)
    }
  }
  const calls = document.querySelector('.calls-scroll')
  if (calls) {
    expect(calls.scrollWidth, 'the diagram scrolls sideways').toBeGreaterThan(calls.clientWidth)
  }
}

async function settle(state: TraceState): Promise<void> {
  if (state.view !== 'own' && !(state.view === 'steps' && state.language === 'oxsts')) {
    await chooseView(state.view)
  }
  await state.act?.()
  await new Promise((resolve) => {
    setTimeout(resolve, 400)
  })
}

afterAll(async () => {
  await page.viewport(1440, 900)
})

describe('the trace area', () => {
  for (const colorMode of ['light', 'dark'] as const) {
    for (const [name, state] of Object.entries(STATES_BY_NAME)) {
      it(`shows ${name} in the desktop column in the ${colorMode} theme`, async () => {
        await page.viewport(1440, 900)
        const language = LANGUAGES[state.language]!
        const { live, shell } = seedWithTrace(language, caseState(language, { validated: state.validated ?? false, recorded: state.recorded ?? true }))
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        onTestFinished(() => {
          root.unmount()
          container.remove()
        })
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} trace={CaseTrace} />)
        await expect.poll(() => {
          return document.querySelector('.monaco-editor .view-line') && document.querySelector('.case-trace')
        }, { timeout: 60_000 }).toBeTruthy()
        await settle(state)
        await state.expectState()
        expectLayout()
        await page.screenshot({ path: `../../build/screens/trace-${name}-${colorMode}.png` })
      })
    }

    for (const name of PHONE_STATES) {
      it(`shows ${name} at phone width in the ${colorMode} theme`, async () => {
        await page.viewport(360, 760)
        document.documentElement.dataset.theme = colorMode
        await initializeMonaco(colorMode)
        const state = STATES_BY_NAME[name]!
        const language = LANGUAGES[state.language]!
        const failed = caseState(language, { validated: true, recorded: true })
        const { live } = seedWithTrace(language, failed)
        const flavor = live.workspace.get().flavor!
        const container = document.createElement('div')
        container.style.cssText = 'display: flex; flex-direction: column; width: 360px; height: 760px; background: var(--surface-panel)'
        document.body.append(container)
        const root = createRoot(container)
        onTestFinished(() => {
          root.unmount()
          container.remove()
        })
        root.render(
          <LiveContext.Provider value={live}>
            <CaseTrace caseState={failed} flavor={flavor} />
          </LiveContext.Provider>,
        )
        await expect.poll(() => {
          return document.querySelector('.case-trace')
        }).toBeTruthy()
        await settle(state)
        await state.expectState()
        expectLayout()
        expect(count('.frame-open'), 'no tab to open').toBe(0)
        await page.screenshot({ path: `../../build/screens/trace-phone-${name}-${colorMode}.png` })
      })
    }
  }
})
