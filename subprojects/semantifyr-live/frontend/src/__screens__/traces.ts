/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Fraction, ReportCall, ReportState, ResultTrace, VerificationReport } from '@semantifyr/editor-common'

import { findExample, liveFlavors } from '../examples'
import type { VerificationCaseState } from '../lib/verification/engine'
import { servedFlavors } from '../__tests__/fixtures/flavors'
import { seedLive, type Seed, type Seeded } from './seededLive'

// A failed case of the level crossing with its trace in each language, and a Live that reads the trace's documents

const SYSML_TRACE_URI = 'file:///workspace/traces/BarrierDownWhileTrainInCrossing.trace.sysml'
const SYSML_WITNESS_URI = 'file:///workspace/.artifacts/snippet.semantifyr.sysml.model.BarrierDownWhileTrainInCrossing/witness.semantifyr'
const GAMMA_TRACE_URI = 'file:///workspace/.artifacts/snippet.LevelCrossing.BarrierDownWhileTrainInCrossing/trace.get'
const GAMMA_WITNESS_URI = 'file:///workspace/.artifacts/snippet.LevelCrossing.BarrierDownWhileTrainInCrossing/witness.semantifyr'
const OXSTS_WITNESS_URI = 'file:///workspace/.artifacts/snippet.example.levelcrossing.BarrierDownWhileTrainInCrossing/witness.semantifyr'

const SYSML_TRACE = `private import Occurrences::HappensJustBefore;

part def BarrierDownWhileTrainInCrossingTrace :> LevelCrossing {
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

function call(self: string, transition: string, calls: ReportCall[] = [], argument?: string): ReportCall {
  return {
    self: `root.system.${self}`,
    transition: `semantifyr::sysml::${transition}`,
    arguments: argument === undefined ? {} : { item: `semantifyr::sysml::model::${argument}` },
    calls,
  }
}

function values(entries: Readonly<Record<string, string | null>>): ReportState {
  return {
    values: Object.fromEntries(Object.entries(entries).map(([variable, value]) => {
      if (value === null) {
        return [`root.system.${variable}`, null]
      }
      return [`root.system.${variable}`, value.startsWith('global_') ? `semantifyr::sysml::model::${value}` : `root.system.${value}`]
    })),
  }
}

const START = {
  'trackA.Behaviour.activeState': 'trackA.Behaviour.Free',
  'trackB.Behaviour.activeState': 'trackB.Behaviour.Free',
  'trackB.controller.outgoingItem': null,
  'controller.Behaviour.activeState': 'controller.Behaviour.Idle',
  'controller.barrier.outgoingItem': null,
  'barrier.Behaviour.activeState': 'barrier.Behaviour.Up',
}

const STEP_ONE = { ...START, 'trackB.Behaviour.activeState': 'trackB.Behaviour.Approaching', 'trackB.controller.outgoingItem': 'global_TrainApproaching' }
const STEP_TWO = { ...STEP_ONE, 'controller.Behaviour.activeState': 'controller.Behaviour.TrainPresent', 'controller.barrier.outgoingItem': 'global_Lower' }
const STEP_THREE = { ...STEP_TWO, 'trackA.Behaviour.activeState': 'trackA.Behaviour.InCrossing', 'barrier.Behaviour.activeState': 'barrier.Behaviour.Down' }

const CALLS = [
  [
    call('trackB.Behaviour.UnnamedTransitionUsage1', 'states::Transition::fire', [call('trackB.controller', 'ports::Port::setOutgoingItem', [], 'global_TrainApproaching')]),
  ],
  [
    call('controller.Behaviour.UnnamedTransitionUsage6', 'states::Transition::fire', [call('controller.barrier', 'ports::Port::setOutgoingItem', [], 'global_Lower')]),
  ],
  [
    call('trackA.Behaviour.UnnamedTransitionUsage3', 'states::Transition::fire'),
  ],
]

// a timed trace waits before each step, a whole amount, a fraction and no time
const ELAPSED: readonly Fraction[] = [
  1,
  '3/2',
  0,
]

function traceOf(witnessUri: string, recorded: boolean, timed: boolean): ResultTrace {
  return {
    root: 'root',
    transitions: [],
    variables: Object.keys(START).map((variable) => {
      return { variable: `root.system.${variable}`, type: { kind: 'class', name: 'semantifyr::Anything', element: null, size: null }, slot: null }
    }),
    initial: { state: 0, calls: [] },
    states: [values(START), values(STEP_ONE), values(STEP_TWO), values(STEP_THREE)],
    steps: CALLS.map((calls, index) => {
      return { source: index, target: index + 1, elapsed: timed ? ELAPSED[index] ?? 0 : 0, calls: recorded ? calls : [] }
    }),
    missingCalls: null,
    witnessUri,
  }
}

export interface Language {
  flavorId: string
  exampleId: string
  caseName: string
  // where the example defines the case
  definition: string
  traceUri: string
}

export const LANGUAGES: Readonly<Record<string, Language>> = {
  sysml: { flavorId: 'sysml', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'verification def BarrierDownWhileTrainInCrossing', traceUri: SYSML_TRACE_URI },
  gamma: { flavorId: 'gamma', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'verification case BarrierDownWhileTrainInCrossing', traceUri: GAMMA_TRACE_URI },
  oxsts: { flavorId: 'oxsts', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'class BarrierDownWhileTrainInCrossing', traceUri: OXSTS_WITNESS_URI },
}

// the run details read the validation's backend, duration and soundness from its report
const VALIDATION = {
  version: 2,
  kind: 're-check',
  verificationCase: 'example::levelcrossing::BarrierDownWhileTrainInCrossing',
  portfolioId: 'smart',
  result: {
    metadata: { backendId: 'spin:safe', startedAt: '2026-11-16T10:00:00Z' },
    verdict: 'Passed',
    soundness: 'UnderApproximating',
    metrics: { totalDuration: 'PT0.6S' },
  },
  trace: null,
} as unknown as VerificationReport

export function caseState(language: Language, options: { validated: boolean, recorded: boolean, timed?: boolean }): VerificationCaseState {
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
    soundness: 'UnderApproximating',
    portfolioId: 'smart',
    trace: traceOf(language.traceUri, options.recorded, options.timed ?? false),
    ...(options.validated
      ? { waited: 30_000, validationWaited: 4_000, witnessValidation: 'valid' as const, validationReport: VALIDATION, validationPortfolioIdUsed: 'smart' }
      : {}),
  }
}

export function seedWithTrace(language: Language, state: VerificationCaseState, others: VerificationCaseState[] = [], seed: Seed = {}): Seeded {
  const seeded = seedLive({ ...seed, flavorId: language.flavorId, exampleId: language.exampleId, verification: { phase: 'done', discovery: 'discovered', cases: [state, ...others] }, selection: state.caseInfo.id })
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
