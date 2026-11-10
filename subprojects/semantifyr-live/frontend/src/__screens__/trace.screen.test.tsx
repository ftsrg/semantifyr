/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { afterAll, describe, expect, it, onTestFinished } from 'vitest'
import { page } from 'vitest/browser'
import type { TraceEntry, VerificationTrace, WitnessStateStep } from '@semantifyr/editor-common'

import { findExample, liveFlavors } from '../examples'
import { initializeMonaco } from '../lib/session/monaco'
import type { VerificationCaseState } from '../lib/verification/engine'
import { LiveContext } from '../lib/workspace/liveContext'
import { ShellRoot } from '../shells/ShellRoot'
import { CaseTrace } from '../trace/CaseTrace'
import { servedFlavors } from '../__tests__/fixtures/flavors'
import { seedLive, type Seeded } from './seededLive'

import '../ui/tokens.css'
import '../ui/components.css'
import '../ui/base.css'

// Goal: each view of the trace in each language shows what tells it apart, inside the desktop column and at phone
// width in both themes; lines wrap, the diagram scrolls sideways, and only the views' own scrollers scroll

const SYSML_TRACE_URI = 'file:///workspace/traces/BarrierDownWhileTrainInCrossingViolated.trace.sysml'
const SYSML_WITNESS_URI = 'file:///workspace/.artifacts/snippet.semantifyr.sysml.model.BarrierDownWhileTrainInCrossingViolated/witness.oxsts'
const GAMMA_TRACE_URI = 'file:///workspace/.artifacts/snippet.LevelCrossing.BarrierDownWhileTrainInCrossing/trace.gamma'
const GAMMA_WITNESS_URI = 'file:///workspace/.artifacts/snippet.LevelCrossing.BarrierDownWhileTrainInCrossing/witness.oxsts'
const OXSTS_WITNESS_URI = 'file:///workspace/.artifacts/snippet.example.levelcrossing.BarrierDownWhileTrainInCrossing/witness.oxsts'

const SYSML_TRACE = `private import Occurrences::HappensJustBefore;

part def BarrierDownWhileTrainInCrossingViolatedTrace :> LevelCrossing {
    part trackB :>> trackB {
        exhibit state Behaviour :>> Behaviour {
            state free1 :> Free;
            action toApproaching1 :> stateTransitions {
                action sendTrainApproaching1 send new TrainApproaching() via controller;
            }
            state approaching1 :> Approaching;
        }
    }
}
`

const GAMMA_TRACE = `package LevelCrossing

trace BarrierDownWhileTrainInCrossingTrace of BarrierDownWhileTrainInCrossing {
    step {
        act {
            raise system.trackB.sensors.approach
        }
        assert {
            transition system.trackB.Main.Free to Approaching
            state system.trackB.Main.Approaching
        }
    }
}
`

const WITNESS = `witness of example::levelcrossing::BarrierDownWhileTrainInCrossing

var step : int := 0

contains root : example::levelcrossing::BarrierDownWhileTrainInCrossing

init {
    assume (step == 0)
    inline root.init()
    step := 1
}
`

const TEXTS: Readonly<Record<string, string>> = {
  [SYSML_TRACE_URI]: SYSML_TRACE,
  [SYSML_WITNESS_URI]: WITNESS,
  [GAMMA_TRACE_URI]: GAMMA_TRACE,
  [GAMMA_WITNESS_URI]: WITNESS,
  [OXSTS_WITNESS_URI]: WITNESS,
}

function call(self: string, transition: string, inner: TraceEntry[] | null = null, argument?: string): TraceEntry {
  return {
    self: `root.system.${self}`,
    calledTransition: `semantifyr::sysml::${transition}`,
    arguments: argument === undefined ? [] : [{ parameter: 'item', value: `semantifyr::sysml::model::${argument}` }],
    innerTraces: inner,
  }
}

function values(entries: Readonly<Record<string, string>>): WitnessStateStep {
  return {
    values: Object.entries(entries).map(([variable, value]) => {
      if (value === 'nothing') {
        return { variable: `root.system.${variable}`, value }
      }
      return { variable: `root.system.${variable}`, value: value.startsWith('global_') ? `semantifyr::sysml::model::${value}` : `root.system.${value}` }
    }),
  }
}

const START = {
  'trackA.Behaviour.activeState': 'trackA.Behaviour.Free',
  'trackB.Behaviour.activeState': 'trackB.Behaviour.Free',
  'trackB.controller.outgoingItem': 'nothing',
  'controller.Behaviour.activeState': 'controller.Behaviour.Idle',
  'controller.barrier.outgoingItem': 'nothing',
  'barrier.Behaviour.activeState': 'barrier.Behaviour.Up',
}

const STEP_ONE = { ...START, 'trackB.Behaviour.activeState': 'trackB.Behaviour.Approaching', 'trackB.controller.outgoingItem': 'global_TrainApproaching' }
const STEP_TWO = { ...STEP_ONE, 'controller.Behaviour.activeState': 'controller.Behaviour.TrainPresent', 'controller.barrier.outgoingItem': 'global_Lower' }
const STEP_THREE = { ...STEP_TWO, 'trackA.Behaviour.activeState': 'trackA.Behaviour.InCrossing', 'barrier.Behaviour.activeState': 'barrier.Behaviour.Down' }

const CALLS = {
  initializedStep: { traces: [] },
  steps: [
    {
      traces: [
        call('trackB.Behaviour.UnnamedTransitionUsage1', 'states::Transition::fire', [call('trackB.controller', 'ports::Port::setOutgoingItem', null, 'global_TrainApproaching')]),
        call('passTime', 'verification::VerificationCaseDefinition::passTime'),
      ],
    },
    {
      traces: [
        call('controller.Behaviour.UnnamedTransitionUsage6', 'states::Transition::fire', [call('controller.barrier', 'ports::Port::setOutgoingItem', null, 'global_Lower')]),
      ],
    },
    {
      traces: [
        call('trackA.Behaviour.UnnamedTransitionUsage3', 'states::Transition::fire'),
      ],
    },
  ],
}

const STATES = {
  initializedStep: values(START),
  steps: [
    values(STEP_ONE),
    values(STEP_TWO),
    values(STEP_THREE),
  ],
}

function traceOf(witnessUri: string, recorded = true): VerificationTrace {
  return {
    callTrace: recorded ? CALLS : { initializedStep: { traces: [] }, steps: CALLS.steps.map(() => ({ traces: [] })) },
    witnessState: STATES,
    witnessUri,
  }
}

interface Language {
  flavorId: string
  exampleId: string
  caseName: string
  // where the example defines the case
  definition: string
  traceUri: string
}

const LANGUAGES: Readonly<Record<string, Language>> = {
  sysml: { flavorId: 'sysml', exampleId: 'level_crossing', caseName: 'BarrierDownWhileTrainInCrossingViolated', definition: 'verification def BarrierDownWhileTrainInCrossingViolated', traceUri: SYSML_TRACE_URI },
  gamma: { flavorId: 'gamma', exampleId: 'LevelCrossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'verification case BarrierDownWhileTrainInCrossing', traceUri: GAMMA_TRACE_URI },
  oxsts: { flavorId: 'oxsts', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'class BarrierDownWhileTrainInCrossing', traceUri: OXSTS_WITNESS_URI },
}

function caseState(language: Language, options: { validated: boolean, recorded: boolean }): VerificationCaseState {
  const flavor = liveFlavors(servedFlavors).find((candidate) => {
    return candidate.id === language.flavorId
  })
  const code = flavor ? findExample(flavor, language.exampleId)?.code ?? '' : ''
  const line = code.split('\n').findIndex((text) => {
    return text.includes(language.definition)
  })
  const fileUri = `file:///workspace/snippet.${language.flavorId}`
  return {
    caseInfo: { id: language.caseName, label: language.caseName, location: { uri: fileUri, range: { start: { line, character: 0 }, end: { line, character: 1 } } } },
    status: 'failed',
    resultStatus: 'failed',
    metrics: { totalDuration: 'PT2.9S', preparationDuration: 'PT0S', verificationDuration: 'PT2.9S', backAnnotationDuration: 'PT0S' },
    backendId: 'theta:cegar',
    portfolioId: 'smart-full',
    trace: traceOf(language.traceUri, options.recorded),
    ...(options.validated
      ? { witnessValidation: 'valid' as const, validationMetrics: { totalDuration: 'PT0.6S', preparationDuration: 'PT0S', verificationDuration: 'PT0.6S', backAnnotationDuration: 'PT0S' }, validationBackendId: 'spin:safe', validationPortfolioIdUsed: 'smart-full' }
      : {}),
  }
}

function seed(language: Language, state: VerificationCaseState): Seeded {
  const seeded = seedLive({ flavorId: language.flavorId, exampleId: language.exampleId, verification: { phase: 'done', discovery: 'discovered', cases: [state] }, selection: state.caseInfo.id })
  const runtime = seeded.live.workspace.get().runtime
  if (runtime) {
    runtime.api.readDocument = (uri) => {
      return Promise.resolve(TEXTS[uri] ?? null)
    }
    runtime.witnessUriOf = (uri) => {
      return uri === SYSML_TRACE_URI ? SYSML_WITNESS_URI : undefined
    }
  }
  return seeded
}

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
        const { live, shell } = seed(language, caseState(language, { validated: state.validated ?? false, recorded: state.recorded ?? true }))
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
        const { live } = seed(language, failed)
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
