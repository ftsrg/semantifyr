/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Location } from '@semantifyr/editor-common'

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
  // ISO-8601 instant (e.g. "2026-05-13T14:22:01.123Z"), wall-clock moment the
  // backend marked itself started.
  startedAt: string
  commit: string
  buildTime: string
  activeSessions: number
  maxSessions: number
}

export type VerificationKind = 'Verify' | 'Validate'

export interface ActiveVerificationInfo {
  verificationId: string
  portfolioId: string
  kind: VerificationKind
  // ISO 8601 duration since start (e.g. "PT3.2S").
  elapsed: string
  // Present over the verificationsChanged channel; absent from the admin REST status.
  location?: Location
}

export interface SessionLspInfo {
  timeSinceLastClientMessage: string
  timeSinceLastServerMessage: string
}

export interface SessionInfo {
  sessionId: string
  remoteIp: string
  flavorId: string
  uptime: string
  workingDirectory: string
  activeVerifications: ActiveVerificationInfo[]
  sessionLspInfo: SessionLspInfo
}

export interface AdminStatusResponse {
  sessions: SessionInfo[]
}

export interface AdminServerConfigResponse {
  port: number
  pingPeriod: string
  pingTimeout: string
  webRootDirectory: string | null
  adminPasswordSet: boolean
  sessionIdleTimeout: string
  wsHandshakesPerPeriod: number
  wsHandshakeRatePeriod: string
  maxWsFrameSize: number
  httpsOnlyCookies: boolean
}

export interface AdminSessionManagerConfigResponse {
  maxSessionsGlobal: number
  semanticLibrariesDirectory: string | null
  rootWorkDirectory: string
}

export interface AdminVerificationConfigResponse {
  concurrency: number
  timeout: string
}

export interface AdminConfigResponse {
  development: boolean
  server: AdminServerConfigResponse
  sessionManager: AdminSessionManagerConfigResponse
  verification: AdminVerificationConfigResponse
}
