/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/**
 * Wire types shared by the live-server's two transport surfaces: the REST API ({@link ./rest})
 * and the JSON-RPC method family that rides over the LSP WebSocket
 * ({@link ./lspExtensions}). Both deliver the same session shape, so the type lives here.
 */

export interface PortfolioInfo {
  id: string
  displayName: string
  description: string
  available: boolean
}

export interface FlavorInfo {
  id: string
  displayName: string
  languageId: string
  fileName: string
  verificationCommand: string | null
  discoveryCommand: string | null
  validateWitnessCommand: string | null
  peekCompiledOutput: boolean
}

export interface InfoResponse {
  uptime: string
  commit: string
  buildTime: string
  activeSessions: number
  maxSessions: number
}

export type VerificationKind = 'Verify' | 'Validate'

export interface ActiveVerificationInfo {
  requestId: string
  kind?: VerificationKind
  caseLabel?: string | null
  portfolioId?: string | null
  /** ISO 8601 duration since the request was enqueued (e.g. "PT3.2S"). */
  elapsed?: string
}

export interface LspProxyInfo {
  clientMessageCount: number
  serverMessageCount: number
  errorCount: number
  timeSinceLastClientMessage: string
  timeSinceLastServerMessage: string
}

/** Session info returned by both /api/admin/status and the semantifyr/live/session/info JSON-RPC method. */
export interface SessionInfo {
  sessionId: string
  remoteIp: string
  flavorId: string
  uptime: string
  workingDirectory: string
  activeVerifications: ActiveVerificationInfo[]
  started: boolean
  bridgeInfo: LspProxyInfo
}

export interface AdminStatusResponse {
  sessions: SessionInfo[]
}

export interface AdminConfigResponse {
  maxSessionsGlobal: number
  maxSessionsPerIp: number
  verificationConcurrency: number
  verificationTimeout: string
}
