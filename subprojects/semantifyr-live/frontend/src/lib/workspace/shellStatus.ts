/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ConnectionState } from '../stores/connection'
import { areCasesKnown, currentStep, isInitialized, type InitializationState, type InitializationStep } from '../stores/initialization'
import type { RunningInterval, VerificationCaseState, VerificationState } from '../verification/engine'
import { caseRowOf, countsOf, countsPhrase, formatSeconds } from './caseRows'
import type { CatalogStatus } from './live'
import type { QueuedVerification, RunTiming } from './verification'

// the clock of a view ticks every second, so a step can start after its last tick
export function stepTimeText(step: InitializationStep, now: number, failedWord: string): string {
  if (step.state === 'done' && step.startedAt !== null && step.finishedAt !== null) {
    return formatSeconds(step.finishedAt - step.startedAt)
  }
  if (step.state === 'running' && step.startedAt !== null) {
    return `${Math.floor(Math.max(0, now - step.startedAt) / 1000)} s`
  }
  return step.state === 'failed' ? failedWord : ''
}

// how long the line that closes a run stays before the status line returns to rest
export const CLOSING_LINE_MS = 8000

export type StatusKind =
  | 'idle'
  | 'busy'
  | 'done'
  | 'warning'
  | 'problem'

export type StatusAction =
  | 'connect'
  | 'reconnect'

export interface ShellStatus {
  kind: StatusKind
  text: string
  // a case name the line names, set in code
  caseName?: string
  // the line says how the unsaved model is saved
  unsaved?: boolean
  // the text after the case name
  after?: string
  action?: StatusAction
  actionLabel?: string
}

export interface ShellStatusInput {
  catalog: CatalogStatus
  languageName: string
  connection: ConnectionState
  initialization: InitializationState
  verification: VerificationState
  timing: RunTiming | null
  queued: QueuedVerification | null
  // Verify all was pressed with errors in the model
  blockedErrors: number | null
  now: number
  // the model has edits no file holds
  unsaved?: boolean
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1)
}

function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}

// the seconds until the next attempt to connect, null when none waits
export function secondsToRetry(connection: ConnectionState, now: number): number | null {
  return connection.retryAt === null ? null : Math.max(0, Math.ceil((connection.retryAt - now) / 1000))
}

function connectionStatus(connection: ConnectionState, now: number): ShellStatus | null {
  const seconds = secondsToRetry(connection, now)
  switch (connection.status) {
    case 'connected':
      return null
    case 'connecting':
      if (!connection.reconnecting) {
        return null
      }
      if (connection.refused) {
        return {
          kind: 'warning',
          text: seconds ? `The server refused the session: it is at capacity. Retrying in ${seconds} s; the wait grows with each refusal.` : 'The server refused the session: it is at capacity. Retrying.',
        }
      }
      return {
        kind: 'warning',
        text: seconds ? `Connection lost. Reconnecting in ${seconds} s.` : 'Connection lost. Reconnecting.',
        action: 'reconnect',
        actionLabel: 'Reconnect now',
      }
    case 'disconnected':
      return {
        kind: 'idle',
        text: 'Disconnected. The editor keeps working; verifying needs the connection.',
        action: 'connect',
        actionLabel: 'Connect',
      }
    case 'failed':
      if (connection.terminated) {
        return {
          kind: 'warning',
          text: `${connection.reason ?? 'The server terminated the session'}. Not reconnecting until you ask.`,
          action: 'reconnect',
          actionLabel: 'Reconnect',
        }
      }
      return {
        kind: 'warning',
        text: `${connection.reason ?? 'The connection failed'}. The editor works; verifying needs a session.`,
        action: 'reconnect',
        actionLabel: 'Reconnect now',
      }
  }
}

function queuedStatus(queued: QueuedVerification, verification: VerificationState, step: InitializationStep): ShellStatus {
  const reason = lowerFirst(step.label)
  const target = verification.cases.find((caseState) => {
    return caseState.caseInfo.id === queued.caseId
  })
  if (!target) {
    return { kind: 'busy', text: `Verify all waits: ${reason}` }
  }
  return { kind: 'busy', text: 'Verify ', caseName: caseRowOf(target).name, after: ` waits: ${reason}` }
}

export interface ServerWait {
  caseState: VerificationCaseState
  validation: boolean
}

// a case the server queues while it runs none of the session's: other sessions hold every permit
export function serverWaitOf(cases: readonly VerificationCaseState[]): ServerWait | null {
  const running = cases.some((caseState) => {
    return caseState.serverState === 'Running' || caseState.validationServerState === 'Running'
  })
  if (running) {
    return null
  }
  const verify = cases.find((caseState) => {
    return caseState.status === 'running' && caseState.serverState === 'Queued'
  })
  if (verify) {
    return { caseState: verify, validation: false }
  }
  const validate = cases.find((caseState) => {
    return caseState.validating === true && caseState.validationServerState === 'Queued'
  })
  return validate ? { caseState: validate, validation: true } : null
}

function serverBusyStatus(wait: ServerWait): ShellStatus {
  return {
    kind: 'busy',
    text: 'The server is busy with other verifications: ',
    caseName: caseRowOf(wait.caseState).name,
    after: wait.validation ? ' waits to be validated' : ' waits',
  }
}

// the time the server ran at least one of them: the length of the union of the intervals
export function runningTimeOf(intervals: readonly RunningInterval[]): number | null {
  const [first, ...rest] = [...intervals].sort((left, right) => {
    return left.start - right.start
  })
  if (first === undefined) {
    return null
  }
  let total = 0
  let { start, end } = first
  for (const interval of rest) {
    if (interval.start > end) {
      total += end - start
      start = interval.start
    }
    end = Math.max(end, interval.end)
  }
  return total + end - start
}

function casesOfRun(verification: VerificationState, timing: RunTiming | null): VerificationCaseState[] {
  return verification.cases.filter((caseState) => {
    return timing?.caseId == null || caseState.caseInfo.id === timing.caseId
  })
}

function cancelledInRun(caseState: VerificationCaseState, timing: RunTiming | null): boolean {
  return caseState.cancelledAt !== undefined && caseState.cancelledAt >= (timing?.startedAt ?? 0)
}

function cancelledPhrase(count: number): string {
  return count === 0 ? '' : `${count} cancelled`
}

// names a case the server runs, or one sent and not yet reported, so the line does not flicker; Verify all says how many are done and cancelled
function runningStatus(verification: VerificationState, timing: RunTiming | null): ShellStatus {
  const reportedRunning = verification.cases.filter((caseState) => {
    return caseState.status === 'running' && caseState.serverState === 'Running'
  })
  const named = reportedRunning[0] ?? verification.cases.find((caseState) => {
    return caseState.status === 'running'
  })
  if (!named) {
    return { kind: 'busy', text: verification.message ?? 'Verifying...' }
  }
  const single = timing !== null && timing.caseId !== null
  const more = reportedRunning.length > 1 ? ` and ${reportedRunning.length - 1} more` : ''
  if (single) {
    const progress = verification.progress ? ` - ${verification.progress}` : ''
    return { kind: 'busy', text: 'Verifying ', caseName: caseRowOf(named).name, after: `${more}${progress}` }
  }
  const cancelled = verification.cases.filter((caseState) => {
    return cancelledInRun(caseState, timing)
  }).length
  const done = verification.cases.filter((caseState) => {
    return caseState.status !== 'running' && caseState.status !== 'queued' && !cancelledInRun(caseState, timing)
  }).length
  const counted = [`${done} of ${verification.cases.length} done`, cancelledPhrase(cancelled)].filter((part) => {
    return part !== ''
  }).join(', ')
  return { kind: 'busy', text: 'Verifying ', caseName: caseRowOf(named).name, after: `${more}, ${counted}` }
}

// the run's time is the server's running time, never the wait in its queue
function closingStatus(verification: VerificationState, timing: RunTiming): ShellStatus | null {
  if (verification.phase === 'cancelled') {
    return { kind: 'done', text: 'Verification cancelled' }
  }
  if (verification.phase !== 'done' || timing.finishedAt === null) {
    return null
  }
  const verified = casesOfRun(verification, timing)
  const ran = runningTimeOf(verified.flatMap((caseState) => {
    return caseState.ranDuring !== undefined && caseState.ranDuring.start >= timing.startedAt ? [caseState.ranDuring] : []
  }))
  const cancelled = verified.filter((caseState) => {
    return cancelledInRun(caseState, timing)
  })
  const answered = verified.filter((caseState) => {
    return !cancelledInRun(caseState, timing)
  })
  const phrase = [countsPhrase(countsOf(answered.map(caseRowOf))), cancelledPhrase(cancelled.length)].filter((part) => {
    return part !== ''
  }).join(', ')
  const done = ran === null ? 'Done' : `Done in ${formatSeconds(ran)}`
  return { kind: 'done', text: phrase === '' ? done : `${done}: ${phrase}` }
}

// outdated results say more than unsaved edits, which the tab's mark shows anyway
function restStatus(verification: VerificationState, unsaved: boolean): ShellStatus {
  const outdated = verification.cases.filter((caseState) => {
    return caseState.outdated === true
  }).length
  if (outdated > 0) {
    return { kind: 'idle', text: `The model changed: ${plural(outdated, 'result')} ${outdated === 1 ? 'is' : 'are'} outdated` }
  }
  if (unsaved) {
    return { kind: 'idle', text: 'Unsaved changes.', unsaved: true }
  }
  if (verification.discovery !== 'discovered' && verification.cases.length === 0) {
    return { kind: 'idle', text: 'Ready' }
  }
  const count = verification.cases.length
  return { kind: 'idle', text: count === 0 ? 'Ready: no verification cases' : `Ready: ${plural(count, 'verification case')}` }
}

export function shellStatus(input: ShellStatusInput): ShellStatus {
  const { connection, initialization, verification, timing } = input
  if (input.catalog === 'waiting') {
    return { kind: 'warning', text: 'The server is not answering. Retrying. Editing and verifying start once it answers.' }
  }
  const wait = serverWaitOf(verification.cases)
  if (wait) {
    return serverBusyStatus(wait)
  }
  if (verification.phase === 'running') {
    return runningStatus(verification, timing)
  }
  const connectionProblem = connectionStatus(connection, input.now)
  if (connectionProblem) {
    return connectionProblem
  }
  const step = currentStep(initialization)
  if (step?.state === 'failed' && step.reason !== undefined) {
    return { kind: 'problem', text: step.reason }
  }
  if (step && input.queued) {
    return queuedStatus(input.queued, verification, step)
  }
  if (step && areCasesKnown(initialization)) {
    return { kind: 'busy', text: step.label }
  }
  if (input.catalog === 'loading' || !isInitialized(initialization)) {
    const label = step ? `: ${lowerFirst(step.label)}` : ''
    return { kind: 'busy', text: `Initializing ${input.languageName}${label}` }
  }
  if (input.blockedErrors !== null && input.blockedErrors > 0) {
    return { kind: 'problem', text: `Verify all did not run: the model has ${plural(input.blockedErrors, 'error')}` }
  }
  if (timing && timing.finishedAt !== null && input.now - timing.finishedAt < CLOSING_LINE_MS) {
    const closing = closingStatus(verification, timing)
    if (closing) {
      return closing
    }
  }
  return restStatus(verification, input.unsaved ?? false)
}
