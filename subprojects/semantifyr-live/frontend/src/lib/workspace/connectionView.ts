/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ConnectionState } from '../stores/connection'
import type { CatalogStatus } from './live'

export type CloudLook =
  | 'connected'
  | 'busy'
  | 'connecting'
  | 'offline'
  | 'lost'
  | 'failure'

export type ConnectionAction =
  | 'disconnect'
  | 'reconnect'
  | 'restart'

export interface ConnectionView {
  look: CloudLook
  // the word beside the cloud when the connection failed
  reason: string | null
  title: string
  heading: string
  detail: string
  action: ConnectionAction | null
  actionLabel: string | null
}

const SESSION_LIMIT = /session limit/i

export function connectionView(
  catalog: CatalogStatus,
  connection: ConnectionState,
  initialized: boolean,
  host: string,
  languageName: string,
): ConnectionView {
  if (catalog === 'waiting') {
    return {
      look: 'failure',
      reason: 'retrying',
      title: 'The server is not answering. Retrying',
      heading: 'The server is not answering',
      detail: `The page could not reach ${host}. The editor shell is shown and the page keeps retrying; your model stays on screen.`,
      action: null,
      actionLabel: null,
    }
  }
  if (catalog === 'loading') {
    return connecting(host)
  }
  switch (connection.status) {
    case 'connecting':
      if (!connection.reconnecting) {
        return connecting(host)
      }
      if (SESSION_LIMIT.test(connection.reason ?? '')) {
        return {
          look: 'failure',
          reason: 'refused, retrying',
          title: 'The server refused the session. Retrying with a backoff',
          heading: 'Session refused',
          detail: 'The server refused the session because it is at capacity. The page retries with a growing wait; your model stays on screen.',
          action: 'reconnect',
          actionLabel: 'Retry now',
        }
      }
      return {
        look: 'lost',
        reason: null,
        title: 'Connection lost, reconnecting',
        heading: 'Connection lost',
        detail: `The page is reconnecting to ${host}; your model stays on screen.`,
        action: 'reconnect',
        actionLabel: 'Retry now',
      }
    case 'connected':
      if (!initialized) {
        return {
          look: 'busy',
          reason: null,
          title: 'Connected, initializing',
          heading: 'Connected',
          detail: `To ${host}. The ${languageName} language server is initializing. Your model is sent only to be verified, and is not stored.`,
          action: 'disconnect',
          actionLabel: 'Disconnect',
        }
      }
      return {
        look: 'connected',
        reason: null,
        title: `Connected to ${host}`,
        heading: 'Connected',
        detail: `To ${host}. The ${languageName} language server is ready. Your model is sent only to be verified, and is not stored.`,
        action: 'disconnect',
        actionLabel: 'Disconnect',
      }
    case 'disconnected':
      return {
        look: 'offline',
        reason: null,
        title: 'Disconnected. Click to connect',
        heading: 'Disconnected',
        detail: 'The editor keeps working; verifying needs the connection.',
        action: 'reconnect',
        actionLabel: 'Connect',
      }
    case 'failed':
      if (connection.terminated) {
        return {
          look: 'failure',
          reason: 'terminated',
          title: 'The session was terminated. Not reconnecting until you ask',
          heading: 'Session terminated',
          detail: `${connection.reason ?? 'The server ended the session'}. The page does not reconnect until you ask; your model stays on screen.`,
          action: 'restart',
          actionLabel: 'Reconnect',
        }
      }
      return {
        look: 'failure',
        reason: 'failed',
        title: connection.reason ?? 'The connection failed',
        heading: 'Connection failed',
        detail: `${connection.reason ?? 'The connection failed'}. Your model stays on screen.`,
        action: 'reconnect',
        actionLabel: 'Retry now',
      }
  }
}

function connecting(host: string): ConnectionView {
  return {
    look: 'connecting',
    reason: null,
    title: `Connecting to ${host}`,
    heading: 'Connecting',
    detail: `To ${host}.`,
    action: null,
    actionLabel: null,
  }
}
