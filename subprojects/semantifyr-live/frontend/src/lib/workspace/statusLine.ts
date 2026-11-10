/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ConnectionState } from '../stores/connection'
import { currentStep, isInitialized, type InitializationState } from '../stores/initialization'
import type { VerificationState } from '../verification/engine'

export interface StatusLine {
  message: string
  busy: boolean
}

function connectionLine(connection: ConnectionState, initialization: InitializationState): StatusLine | null {
  switch (connection.status) {
    case 'connected':
      return null
    case 'connecting': {
      const step = currentStep(initialization)
      const fallback = connection.reconnecting ? 'Reconnecting...' : step ? `${step.label}...` : 'Initializing...'
      return { message: connection.reason ?? fallback, busy: true }
    }
    case 'disconnected':
      return { message: 'Disconnected', busy: false }
    case 'failed':
      return { message: connection.reason ?? (connection.terminated ? 'Session ended' : 'Connection error'), busy: false }
  }
}

export function statusLine(
  verification: VerificationState,
  connection: ConnectionState,
  initialization: InitializationState,
): StatusLine {
  if (verification.phase === 'running') {
    const running = verification.cases.find((caseState) => {
      return caseState.status === 'running'
    })
    if (running) {
      const progress = verification.progress ? ` - ${verification.progress}` : ''
      return { message: `Verifying ${running.caseInfo.label}${progress}`, busy: true }
    }
    return { message: verification.message ?? 'Running verification...', busy: true }
  }
  const connectionProblem = connectionLine(connection, initialization)
  if (connectionProblem) {
    return connectionProblem
  }
  const step = currentStep(initialization)
  if (!isInitialized(initialization) && step) {
    return { message: `${step.label}...`, busy: step.state === 'running' }
  }
  return { message: verification.message ?? 'Ready', busy: false }
}
