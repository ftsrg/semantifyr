/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type InitializationStepState =
  | 'pending'
  | 'running'
  | 'done'
  | 'failed'

export interface InitializationStepDefinition {
  id: string
  label: string
}

export interface InitializationStep extends InitializationStepDefinition {
  state: InitializationStepState
  startedAt: number | null
  finishedAt: number | null
  // what went wrong, for a failed step that knows
  reason?: string
}

export interface InitializationState {
  steps: readonly InitializationStep[]
}

export const InitializationSteps = {
  connect: { id: 'connect', label: 'Connecting to the server' },
  server: { id: 'server', label: 'Starting the language server' },
  // ends at the model's first diagnostics; it does not hold the cases back, only the start of a verification
  initialize: { id: 'initialize', label: 'Initializing the language server' },
  cases: { id: 'cases', label: 'Finding the verification cases' },
} as const

export function initializationOf(definitions: readonly InitializationStepDefinition[]): InitializationState {
  return {
    steps: definitions.map((definition) => {
      return { ...definition, state: 'pending', startedAt: null, finishedAt: null }
    }),
  }
}

function changeStep(
  state: InitializationState,
  id: string,
  change: (step: InitializationStep) => InitializationStep,
): InitializationState {
  const step = state.steps.find((candidate) => {
    return candidate.id === id
  })
  if (!step) {
    return state
  }
  const changed = change(step)
  if (changed === step) {
    return state
  }
  return {
    steps: state.steps.map((candidate) => {
      return candidate === step ? changed : candidate
    }),
  }
}

export function startStep(state: InitializationState, id: string, now: number): InitializationState {
  return changeStep(state, id, (step) => {
    if (step.state === 'running' || step.state === 'done') {
      return step
    }
    return { id: step.id, label: step.label, state: 'running', startedAt: step.startedAt ?? now, finishedAt: null }
  })
}

export function finishStep(state: InitializationState, id: string, now: number): InitializationState {
  return changeStep(state, id, (step) => {
    if (step.state === 'done') {
      return step
    }
    return { id: step.id, label: step.label, state: 'done', startedAt: step.startedAt ?? now, finishedAt: now }
  })
}

export function failStep(state: InitializationState, id: string, now: number, reason?: string): InitializationState {
  return changeStep(state, id, (step) => {
    if (step.state === 'done' || step.state === 'failed') {
      return step
    }
    return { ...step, state: 'failed', finishedAt: now, ...(reason === undefined ? {} : { reason }) }
  })
}

export function isInitialized(state: InitializationState): boolean {
  return state.steps.every((step) => {
    return step.state === 'done'
  })
}

export function areCasesKnown(state: InitializationState): boolean {
  return state.steps.some((step) => {
    return step.id === InitializationSteps.cases.id && step.state === 'done'
  })
}

// a language server whose initialization failed is left to answer the verification itself
export function isReadyToVerify(state: InitializationState): boolean {
  return state.steps.length > 0 && state.steps.every((step) => {
    return step.state === 'done' || (step.id === InitializationSteps.initialize.id && step.state === 'failed')
  })
}

// a failed step, else the step started last of those running
export function currentStep(state: InitializationState): InitializationStep | undefined {
  return state.steps.find((step) => {
    return step.state === 'failed'
  }) ?? state.steps.findLast((step) => {
    return step.state === 'running'
  })
}
