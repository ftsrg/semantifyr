/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ConnectionState } from '../stores/connection'
import { currentStep, isInitialized, type InitializationState } from '../stores/initialization'
import type { VerificationState } from '../verification/engine'
import { caseRowOf, countsOf, countsPhrase, formatSeconds } from './caseRows'
import type { CatalogStatus } from './live'
import type { RunTiming } from './verification'

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
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1)
}

function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}

function connectionStatus(connection: ConnectionState): ShellStatus | null {
  switch (connection.status) {
    case 'connected':
    case 'connecting':
      if (connection.reconnecting && connection.reason !== null) {
        return { kind: 'warning', text: connection.reason }
      }
      return null
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

function runningStatus(verification: VerificationState): ShellStatus {
  const index = verification.cases.findIndex((caseState) => {
    return caseState.status === 'running'
  })
  const running = verification.cases[index]
  if (!running) {
    return { kind: 'busy', text: verification.message ?? 'Verifying...' }
  }
  const waiting = verification.cases.filter((caseState) => {
    return caseState.status === 'queued' || caseState.status === 'running'
  }).length
  const position = waiting > 1 || index > 0 ? `, ${index + 1} of ${verification.cases.length}` : ''
  const progress = verification.progress ? ` - ${verification.progress}` : ''
  return { kind: 'busy', text: 'Verifying ', caseName: running.caseInfo.label, after: `${position}${progress}` }
}

function closingStatus(verification: VerificationState, timing: RunTiming): ShellStatus | null {
  if (verification.phase === 'cancelled') {
    return { kind: 'done', text: 'Verification cancelled' }
  }
  if (verification.phase !== 'done' || timing.finishedAt === null) {
    return null
  }
  const time = formatSeconds(timing.finishedAt - timing.startedAt)
  if (timing.caseId !== null) {
    const verified = verification.cases.find((caseState) => {
      return caseState.caseInfo.id === timing.caseId
    })
    const word = verified ? caseRowOf(verified).word.toLowerCase() : 'done'
    return { kind: 'done', text: `Done in ${time}: ${word}` }
  }
  const counts = countsOf(verification.cases.map(caseRowOf))
  return { kind: 'done', text: `Done in ${time}: ${countsPhrase(counts)}` }
}

function restStatus(verification: VerificationState): ShellStatus {
  if (verification.discovery !== 'discovered' && verification.cases.length === 0) {
    return { kind: 'idle', text: 'Ready' }
  }
  const outdated = verification.cases.filter((caseState) => {
    return caseState.outdated === true
  }).length
  if (outdated > 0) {
    return { kind: 'idle', text: `The model changed: ${plural(outdated, 'result')} ${outdated === 1 ? 'is' : 'are'} outdated` }
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
    return runningStatus(verification)
  }
  const connectionProblem = connectionStatus(connection)
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
  return restStatus(verification)
}
