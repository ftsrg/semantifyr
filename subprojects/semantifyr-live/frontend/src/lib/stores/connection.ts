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
  connectedSince: number | null
  reconnectCount: number
}

export const INITIAL_CONNECTION: ConnectionState = {
  status: 'connecting',
  reason: null,
  reconnecting: false,
  terminated: false,
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
    connectedSince: now,
    reconnectCount: previous.reconnecting ? previous.reconnectCount + 1 : previous.reconnectCount,
  }
}

export function connectionLost(
  previous: ConnectionState,
  status: Exclude<ConnectionStatus, 'connected'>,
  reason: string | null,
  options: { reconnecting?: boolean, terminated?: boolean } = {},
): ConnectionState {
  return {
    status,
    reason,
    reconnecting: options.reconnecting ?? false,
    terminated: options.terminated ?? false,
    connectedSince: null,
    reconnectCount: previous.reconnectCount,
  }
}
