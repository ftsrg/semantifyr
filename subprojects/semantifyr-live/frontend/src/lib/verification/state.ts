/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type {
  Location,
  VerificationCaseSpecification,
  VerificationReport,
  WitnessValidationStatus,
} from '@semantifyr/editor-common'

import type { ActiveVerificationInfo, VerificationState as ServerState } from '../api/types'

export type VerificationCaseStatus =
  | 'stale'
  | 'queued'
  // sent to the server, which reports it queued or running in serverState
  | 'running'
  | 'passed'
  | 'failed'
  | 'inconclusive'
  | 'timeout'
  | 'not_supported'
  | 'errored'
  | 'cancelled'

export interface VerificationCaseState {
  caseInfo: VerificationCaseSpecification
  status: VerificationCaseStatus
  message?: string
  report?: VerificationReport
  witnessUri?: string
  traceUri?: string
  witnessValidation?: WitnessValidationStatus
  validationMessage?: string | undefined
  // True between the trace arriving and the follow-up validateWitness resolving.
  validating?: boolean
  // what the server last reported of the case it was sent, absent before its first report
  serverState?: ServerState
  validationServerState?: ServerState
  // when the server was first reported running the case, by the page's clock
  runningSince?: number
  // from runningSince to the result or the cancel of the run that left this state
  ranDuring?: RunningInterval
  // when the case was cancelled, by the page's clock
  cancelledAt?: number
  // when the case waiting or running now was asked for, by the page's clock
  askedAt?: number
  // from askedAt to the server's first Running report, for the result shown
  waited?: number
  validationAskedAt?: number
  validationWaited?: number
  portfolioId?: string
  validationReport?: VerificationReport
  validationPortfolioIdUsed?: string
  // the status of the result shown, kept while a new run of the case is queued or running
  resultStatus?: VerificationCaseStatus
  // the model was edited after the result shown was verified
  outdated?: boolean
  editedDuringRun?: boolean
}

export interface RunningInterval {
  start: number
  end: number
}

export type VerificationPhase = 'idle' | 'running' | 'done' | 'error' | 'cancelled'

export type DiscoveryPhase = 'idle' | 'discovering' | 'discovered'

export interface VerificationState {
  phase: VerificationPhase
  discovery: DiscoveryPhase
  message?: string
  progress?: string | undefined
  cases: VerificationCaseState[]
}

export function ranDuringOf(caseState: VerificationCaseState, end: number): { ranDuring?: RunningInterval } {
  return caseState.runningSince === undefined ? {} : { ranDuring: { start: caseState.runningSince, end } }
}

export function cancelledCase(caseState: VerificationCaseState): VerificationCaseState {
  const now = Date.now()
  if (caseState.resultStatus === undefined) {
    return { caseInfo: caseState.caseInfo, status: 'stale', message: 'Verification cancelled', cancelledAt: now, ...ranDuringOf(caseState, now) }
  }
  const { editedDuringRun: _editedDuringRun, serverState: _serverState, runningSince: _runningSince, askedAt: _askedAt, ...result } = caseState
  return { ...result, status: caseState.resultStatus, cancelledAt: now, ...ranDuringOf(caseState, now) }
}

function isWaiting(caseState: VerificationCaseState): boolean {
  return caseState.status === 'queued' || caseState.status === 'running'
}

// cases asked for before they can be sent; null asks for every case
export function markQueued(state: VerificationState, caseId: string | null, askedAt: number): VerificationState {
  if (!state.cases.some((caseState) => {
    return (caseId === null || caseState.caseInfo.id === caseId) && !isWaiting(caseState)
  })) {
    return state
  }
  return {
    ...state,
    cases: state.cases.map((caseState) => {
      return (caseId === null || caseState.caseInfo.id === caseId) && !isWaiting(caseState)
        ? { ...caseState, status: 'queued' as const, askedAt }
        : caseState
    }),
  }
}

export function unmarkQueued(state: VerificationState): VerificationState {
  if (!state.cases.some((caseState) => {
    return caseState.status === 'queued'
  })) {
    return state
  }
  return {
    ...state,
    cases: state.cases.map((caseState) => {
      return caseState.status === 'queued' ? cancelledCase(caseState) : caseState
    }),
  }
}

function sameStart(location: Location, other: Location): boolean {
  return location.uri === other.uri
    && location.range.start.line === other.range.start.line
    && location.range.start.character === other.range.start.character
}

function reportedState(
  active: readonly ActiveVerificationInfo[],
  kind: ActiveVerificationInfo['kind'],
  matches: (location: Location) => boolean,
): ServerState | undefined {
  return active.find((entry) => {
    return entry.kind === kind && entry.location !== undefined && matches(entry.location)
  })?.state
}

// a case the report leaves out has not been registered yet or is finishing, so it keeps what was last reported
function withReport(caseState: VerificationCaseState, active: readonly ActiveVerificationInfo[], now: number): VerificationCaseState {
  let changed = caseState
  if (caseState.status === 'running') {
    const reported = reportedState(active, 'Verify', (location) => {
      return sameStart(location, caseState.caseInfo.location)
    })
    if (reported !== undefined && reported !== caseState.serverState) {
      changed = { ...changed, serverState: reported }
      if (reported === 'Running' && changed.runningSince === undefined) {
        changed = { ...changed, runningSince: now }
      }
    }
  }
  if (caseState.validating === true) {
    const witnessUri = caseState.witnessUri
    const reported = reportedState(active, 'Validate', (location) => {
      return location.uri === witnessUri || sameStart(location, caseState.caseInfo.location)
    })
    if (reported !== undefined && reported !== caseState.validationServerState) {
      changed = { ...changed, validationServerState: reported }
      if (reported === 'Running' && changed.validationWaited === undefined && changed.validationAskedAt !== undefined) {
        changed = { ...changed, validationWaited: now - changed.validationAskedAt }
      }
    }
  }
  return changed
}

export function applyServerReport(
  state: VerificationState,
  active: readonly ActiveVerificationInfo[],
  now: number,
): VerificationState {
  const cases = state.cases.map((caseState) => {
    return withReport(caseState, active, now)
  })
  return cases.every((caseState, index) => {
    return caseState === state.cases[index]
  })
    ? state
    : { ...state, cases }
}

export function markEdited(state: VerificationState): VerificationState {
  return {
    ...state,
    cases: state.cases.map((caseState) => {
      if (caseState.status === 'running') {
        return { ...caseState, editedDuringRun: true }
      }
      if (caseState.resultStatus !== undefined && caseState.outdated !== true) {
        return { ...caseState, outdated: true }
      }
      return caseState
    }),
  }
}

export function mergeDiscoveredCases(
  previous: readonly VerificationCaseState[],
  discovered: readonly VerificationCaseSpecification[],
): VerificationCaseState[] {
  const existingById = new Map(previous.map((caseState) => [caseState.caseInfo.id, caseState]))
  return discovered.map((caseInfo) => {
    const existing = existingById.get(caseInfo.id)
    if (existing) {
      return { ...existing, caseInfo }
    }
    return { caseInfo, status: 'stale' as const }
  })
}
