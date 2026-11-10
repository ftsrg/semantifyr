/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Location } from '@semantifyr/editor-common'

export type { FlavorInfo, PortfolioInfo, PortfoliosResponse } from '@semantifyr/editor-common'

export interface InfoResponse {
  uptime: string
  // ISO-8601 instant (e.g. "2026-05-13T14:22:01.123Z"), wall-clock moment the
  // backend marked itself started.
  startedAt: string
  version: string
  commit: string
  buildTime: string
  activeSessions: number
  maxSessions: number
}

export type VerificationKind = 'Verify' | 'Validate'

export type VerificationState = 'Queued' | 'Running'

export interface ActiveVerificationInfo {
  verificationId: string
  portfolioId: string
  kind: VerificationKind
  state: VerificationState
  // ISO 8601 duration since start (e.g. "PT3.2S").
  elapsed: string
  // Present over the verificationsChanged channel; absent from the admin REST status.
  location?: Location
}

export interface SessionInfo {
  sessionId: string
  remoteIp: string
  flavorId: string
  kind: string
  uptime: string
  runningCount: number
  queuedCount: number
  workingDirectory: string
  activeVerifications: ActiveVerificationInfo[]
}

export interface AdminLoadResponse {
  activeSessions: number
  maxSessions: number
  runningVerifications: number
  queuedVerifications: number
  verificationPermits: number
  uptime: string
}

export interface AdminStatusResponse {
  sessions: SessionInfo[]
  load: AdminLoadResponse
}

export interface AdminServerConfigResponse {
  port: number
  pingPeriod: string
  pingTimeout: string
  webRootDirectory: string | null
  wsHandshakesPerPeriod: number
  wsHandshakeRatePeriod: string
  maxWsFrameSize: number
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

export interface AdminWhoamiResponse {
  user: string
}

export interface AdminPortfolioResponse {
  id: string
  engines: string[]
}

export interface AdminConfigResponse {
  server: AdminServerConfigResponse
  sessionManager: AdminSessionManagerConfigResponse
  verification: AdminVerificationConfigResponse
  portfolios: AdminPortfolioResponse[]
}
