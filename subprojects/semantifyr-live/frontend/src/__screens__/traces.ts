/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { TraceEntry, VerificationTrace, WitnessStateStep } from '@semantifyr/editor-common'

import { findExample, liveFlavors } from '../examples'
import type { VerificationCaseState } from '../lib/verification/engine'
import { servedFlavors } from '../__tests__/fixtures/flavors'
import { seedLive, type Seed, type Seeded } from './seededLive'

// A failed case of the level crossing with its trace in each language, and a Live that reads the trace's documents

const SYSML_TRACE_URI = 'file:///workspace/traces/BarrierDownWhileTrainInCrossingViolated.trace.sysml'
const SYSML_WITNESS_URI = 'file:///workspace/.artifacts/snippet.semantifyr.sysml.model.BarrierDownWhileTrainInCrossingViolated/witness.oxsts'
const GAMMA_TRACE_URI = 'file:///workspace/.artifacts/snippet.LevelCrossing.BarrierDownWhileTrainInCrossing/trace.get'
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

export interface Language {
  flavorId: string
  exampleId: string
  caseName: string
  // where the example defines the case
  definition: string
  traceUri: string
}

export const LANGUAGES: Readonly<Record<string, Language>> = {
  sysml: { flavorId: 'sysml', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossingViolated', definition: 'verification def BarrierDownWhileTrainInCrossingViolated', traceUri: SYSML_TRACE_URI },
  gamma: { flavorId: 'gamma', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'verification case BarrierDownWhileTrainInCrossing', traceUri: GAMMA_TRACE_URI },
  oxsts: { flavorId: 'oxsts', exampleId: 'level-crossing', caseName: 'BarrierDownWhileTrainInCrossing', definition: 'class BarrierDownWhileTrainInCrossing', traceUri: OXSTS_WITNESS_URI },
}

export function caseState(language: Language, options: { validated: boolean, recorded: boolean }): VerificationCaseState {
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
      ? { waited: 30_000, validationWaited: 4_000, witnessValidation: 'valid' as const, validationMetrics: { totalDuration: 'PT0.6S', preparationDuration: 'PT0S', verificationDuration: 'PT0.6S', backAnnotationDuration: 'PT0S' }, validationBackendId: 'spin:safe', validationPortfolioIdUsed: 'smart-full' }
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
