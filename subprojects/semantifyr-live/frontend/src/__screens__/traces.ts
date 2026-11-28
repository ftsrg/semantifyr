/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Fraction, ReportCall, ReportState, ReportTrace } from '@semantifyr/editor-common'

import { findExample, liveFlavors } from '../examples'
import type { VerificationCaseState } from '../lib/verification/state'
import { servedFlavors } from '../__tests__/fixtures/flavors'
import { reportOf } from '../__tests__/fixtures/reports'
import { seedLive, type Seed, type Seeded } from './seededLive'

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

const ELAPSED: readonly Fraction[] = [
  1,
  '3/2',
  0,
]

function traceOf(recorded: boolean, timed: boolean): ReportTrace {
  return {
    transitions: [],
    variables: Object.keys(START).map((variable) => {
      return { variable: `root.system.${variable}`, type: { kind: 'class', name: 'semantifyr::Anything' }, slot: null }
    }),
    initial: { state: 0, calls: [] },
    states: [values(START), values(STEP_ONE), values(STEP_TWO), values(STEP_THREE)],
    steps: CALLS.map((calls, index) => {
      return { source: index, target: index + 1, elapsed: timed ? ELAPSED[index] ?? 0 : 0, calls: recorded ? calls : [] }
    }),
  }
}

export interface Language {
  flavorId: string
  exampleId: string
  caseName: string
  definition: string
  witnessUri: string
  // the language's own trace, absent for a Semantifyr model
  traceUri?: string
}

export const LANGUAGES: Readonly<Record<string, Language>> = {
  sysml: { flavorId: 'sysml', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'verification def BarrierDownWhileTrainInCrossing', witnessUri: SYSML_WITNESS_URI, traceUri: SYSML_TRACE_URI },
  gamma: { flavorId: 'gamma', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'verification case BarrierDownWhileTrainInCrossing', witnessUri: GAMMA_WITNESS_URI, traceUri: GAMMA_TRACE_URI },
  oxsts: { flavorId: 'oxsts', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'class BarrierDownWhileTrainInCrossing', witnessUri: OXSTS_WITNESS_URI },
}

const VALIDATION = reportOf({ verdict: 'Passed', backendId: 'spin:safe', seconds: 0.6, soundness: 'UnderApproximating', kind: 're-check' })

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
    portfolioId: 'smart',
    report: reportOf({ verdict: 'Failed', backendId: 'theta:cegar', seconds: 2.9, soundness: 'UnderApproximating', trace: traceOf(options.recorded, options.timed ?? false) }),
    witnessUri: language.witnessUri,
    ...(language.traceUri ? { traceUri: language.traceUri } : {}),
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
  }
  return seeded
}
