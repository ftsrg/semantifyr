/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type {
  ActiveVerificationInfo,
  AdminConfigResponse,
  AdminLoadResponse,
  AdminPortfolioResponse,
  AdminServerConfigResponse,
  AdminSessionManagerConfigResponse,
  AdminStatusResponse,
  AdminVerificationConfigResponse,
  FlavorInfo,
  InfoResponse,
  PortfolioInfo,
  SessionInfo,
  VerificationKind,
  VerificationState,
} from './types'

export { ApiError, createApi, type LiveServerApi } from './rest'

export {
  SemantifyrLiveApi,
  SemantifyrLiveMethods,
  type CancelVerificationParams,
  type NotificationListenerRegistrar,
  type VerificationsChangedParams,
} from './lspExtensions'
