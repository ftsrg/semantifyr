/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type ConnectionStatus =
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'failed'

export interface ConnectionState {
  status: ConnectionStatus
  reason: string | null
  reconnecting: boolean
  // a failure the session does not retry: a reconnect needs a reload
  terminated: boolean
  // the server is at capacity and refused the session
  refused: boolean
  // when the next attempt to connect starts, while one waits
  retryAt: number | null
  connectedSince: number | null
  reconnectCount: number
}

export const INITIAL_CONNECTION: ConnectionState = {
  status: 'connecting',
  reason: null,
  reconnecting: false,
  terminated: false,
  refused: false,
  retryAt: null,
  connectedSince: null,
  reconnectCount: 0,
}

export function connectionConnected(previous: ConnectionState, now: number): ConnectionState {
  if (previous.status === 'connected') {
    return previous
  }
  return {
    status: 'connected',
    reason: null,
    reconnecting: false,
    terminated: false,
    refused: false,
    retryAt: null,
    connectedSince: now,
    reconnectCount: previous.reconnecting ? previous.reconnectCount + 1 : previous.reconnectCount,
  }
}

export function connectionLost(
  previous: ConnectionState,
  status: Exclude<ConnectionStatus, 'connected'>,
  reason: string | null,
  options: { reconnecting?: boolean, terminated?: boolean, refused?: boolean, retryAt?: number | null } = {},
): ConnectionState {
  return {
    status,
    reason,
    reconnecting: options.reconnecting ?? false,
    terminated: options.terminated ?? false,
    refused: options.refused ?? false,
    retryAt: options.retryAt ?? null,
    connectedSince: null,
    reconnectCount: previous.reconnectCount,
  }
}
