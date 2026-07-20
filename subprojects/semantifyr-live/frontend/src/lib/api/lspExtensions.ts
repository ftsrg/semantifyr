/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ActiveVerificationInfo, SessionInfo } from './types'
import type { LspClient } from '../verification/engine'

// Keep in sync with the backend's SemantifyrLiveMethods.
export const SemantifyrLiveMethods = {
  SESSION_INFO: 'semantifyr/live/session/info',
  READ_DOCUMENT: 'semantifyr/live/session/document/read',
  LIST_VERIFICATIONS: 'semantifyr/live/session/verification/list',
  CANCEL_VERIFICATION: 'semantifyr/live/session/verification/cancel',
  CANCEL_ALL_VERIFICATIONS: 'semantifyr/live/session/verification/cancel/all',
  VERIFICATIONS_CHANGED: 'semantifyr/live/session/verification/changed',
} as const

export interface CancelVerificationParams {
  verificationId: string
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

  async cancelVerification(verificationId: string): Promise<boolean> {
    const client = this.getClient()
    if (!client) {
      return false
    }
    const params: CancelVerificationParams = { verificationId }
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
