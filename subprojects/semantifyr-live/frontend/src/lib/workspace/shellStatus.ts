/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ConnectionState } from '../stores/connection'
import { currentStep, isInitialized, type InitializationState, type InitializationStep } from '../stores/initialization'
import type { VerificationState } from '../verification/engine'
import { caseRowOf, countsOf, countsPhrase, formatSeconds } from './caseRows'
import type { CatalogStatus } from './live'
import type { RunTiming } from './verification'

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

// a single run names its case, Verify all its position among the cases
function runningStatus(verification: VerificationState, timing: RunTiming | null): ShellStatus {
  const index = verification.cases.findIndex((caseState) => {
    return caseState.status === 'running'
  })
  const running = verification.cases[index]
  if (!running) {
    return { kind: 'busy', text: verification.message ?? 'Verifying...' }
  }
  const single = timing !== null && timing.caseId !== null
  const position = single ? '' : `, ${index + 1} of ${verification.cases.length}`
  const progress = verification.progress ? ` - ${verification.progress}` : ''
  return { kind: 'busy', text: 'Verifying ', caseName: caseRowOf(running).name, after: `${position}${progress}` }
}

function closingStatus(verification: VerificationState, timing: RunTiming): ShellStatus | null {
  if (verification.phase === 'cancelled') {
    return { kind: 'done', text: 'Verification cancelled' }
  }
  if (verification.phase !== 'done' || timing.finishedAt === null) {
    return null
  }
  const time = formatSeconds(timing.finishedAt - timing.startedAt)
  const verified = verification.cases.filter((caseState) => {
    return timing.caseId === null || caseState.caseInfo.id === timing.caseId
  })
  const phrase = countsPhrase(countsOf(verified.map(caseRowOf)))
  return { kind: 'done', text: phrase === '' ? `Done in ${time}` : `Done in ${time}: ${phrase}` }
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
    return { kind: 'warning', text: 'The server is not answering. Retrying. The editor works; verifying needs a session.' }
  }
  if (verification.phase === 'running') {
    return runningStatus(verification, timing)
  }
  const connectionProblem = connectionStatus(connection, input.now)
  if (connectionProblem) {
    return connectionProblem
  }
  const step = currentStep(initialization)
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
