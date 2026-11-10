/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ConnectionState } from '../../lib/stores/connection'

export type LiveEditorStatus =
  | 'initializing'
  | 'connected'
  | 'disconnected'
  | 'reconnecting'
  | 'errored'
  | 'terminated'

export function editorStatusOf(connection: ConnectionState): LiveEditorStatus {
  switch (connection.status) {
    case 'connecting':
      return connection.reconnecting ? 'reconnecting' : 'initializing'
    case 'connected':
      return 'connected'
    case 'disconnected':
      return 'disconnected'
    case 'failed':
      return connection.terminated ? 'terminated' : 'errored'
  }
}
