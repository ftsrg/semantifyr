/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type {
  ActiveVerificationInfo,
  AdminConfigResponse,
  AdminStatusResponse,
  FlavorInfo,
  InfoResponse,
  LspProxyInfo,
  PortfolioInfo,
  SessionInfo,
  VerificationKind,
} from './types'

export { createApi, type LiveServerApi } from './rest'

export {
  SemantifyrLiveApi,
  SemantifyrLiveMethods,
  type CancelVerificationParams,
  type NotificationListenerRegistrar,
  type VerificationsChangedParams,
} from './lspExtensions'

export {
  decodeCompressedBase64Url,
  encodeCompressedBase64Url,
  normalizeBaseUrl,
  type LiveServerUrls,
} from './urls'
