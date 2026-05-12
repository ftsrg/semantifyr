/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ActiveVerificationInfo, SessionInfo } from './types'
import type { LspClient } from '../verification/engine'

/**
 * Catalog of custom JSON-RPC methods the live-server exposes over the LSP WebSocket.
 * These methods are intercepted by the live-server and never forwarded to the LSP child.
 * The names mirror the backend's {@code SemantifyrLiveMethods} object; keep them in sync.
 */
export const SemantifyrLiveMethods = {
  SESSION_INFO: 'semantifyr/live/session/info',
  READ_DOCUMENT: 'semantifyr/live/session/document/read',
  LIST_VERIFICATIONS: 'semantifyr/live/session/verification/list',
  CANCEL_VERIFICATION: 'semantifyr/live/session/verification/cancel',
  CANCEL_ALL_VERIFICATIONS: 'semantifyr/live/session/verification/cancel/all',
  VERIFICATIONS_CHANGED: 'semantifyr/live/session/verification/changed',
} as const

export interface CancelVerificationParams {
  requestId: string
}

export interface VerificationsChangedParams {
  active?: ActiveVerificationInfo[]
}

export interface ReadDocumentParams {
  uri: string
}

export interface ReadDocumentResult {
  text: string | null
}

export type NotificationListenerRegistrar = (
  method: string,
  listener: (params: unknown) => void,
) => () => void

/**
 * Typed wrapper around the live-server's custom JSON-RPC method family. All transport state
 * lives behind {@code getClient} and {@code addNotificationListener}: the API resolves them
 * lazily so the surface stays valid across reconnects.
 */
export class SemantifyrLiveApi {
  private readonly getClient: () => LspClient | undefined
  private readonly addNotificationListener: NotificationListenerRegistrar

  constructor(
    getClient: () => LspClient | undefined,
    addNotificationListener: NotificationListenerRegistrar,
  ) {
    this.getClient = getClient
    this.addNotificationListener = addNotificationListener
  }

  async getSessionInfo(): Promise<SessionInfo | null> {
    const client = this.getClient()
    if (!client) {
      return null
    }
    return (await client.sendRequest(SemantifyrLiveMethods.SESSION_INFO)) as SessionInfo
  }

  /**
   * Fetches the current text of a workspace document by URI (the in-memory copy if it is
   * open, otherwise the on-disk content). The live-server holds the back-annotated witness
   * on disk under {@code file:///workspace/.artifacts/...}; the browser has no filesystem,
   * so the witness Raw view pulls the source through this request.
   */
  async readDocument(uri: string): Promise<string | null> {
    const client = this.getClient()
    if (!client) {
      return null
    }
    const params: ReadDocumentParams = { uri }
    const result = (await client.sendRequest(SemantifyrLiveMethods.READ_DOCUMENT, params)) as ReadDocumentResult | null
    return result?.text ?? null
  }

  async listVerifications(): Promise<VerificationsChangedParams> {
    const client = this.getClient()
    if (!client) {
      return { active: [] }
    }
    const result = (await client.sendRequest(SemantifyrLiveMethods.LIST_VERIFICATIONS)) as VerificationsChangedParams | null
    return result ?? { active: [] }
  }

  async cancelVerification(requestId: string): Promise<boolean> {
    const client = this.getClient()
    if (!client) {
      return false
    }
    const params: CancelVerificationParams = { requestId }
    return (await client.sendRequest(SemantifyrLiveMethods.CANCEL_VERIFICATION, params)) as boolean
  }

  async cancelAllVerifications(): Promise<number> {
    const client = this.getClient()
    if (!client) {
      return 0
    }
    return (await client.sendRequest(SemantifyrLiveMethods.CANCEL_ALL_VERIFICATIONS)) as number
  }

  onVerificationsChanged(handler: (params: VerificationsChangedParams) => void): () => void {
    return this.addNotificationListener(SemantifyrLiveMethods.VERIFICATIONS_CHANGED, (params) => {
      handler((params as VerificationsChangedParams | undefined) ?? { active: [] })
    })
  }
}
