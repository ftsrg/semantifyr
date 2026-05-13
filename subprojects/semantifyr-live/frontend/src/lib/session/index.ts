/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export {
  createLiveEditorSession,
  type LiveEditorSession,
  type LiveEditorSessionOptions,
  type LiveEditorStatus,
  type ProblemEntry,
} from './liveEditorSession'

export {
  applyColorTheme,
  connectLanguageClient,
  createEditor,
  createFileUri,
  createSecondaryEditor,
  type EditorInstance,
  type LanguageClientCallbacks,
  type LanguageClientConnection,
  type SecondaryEditorHandle,
} from './monaco'

export {
  wrapClientWithMetrics,
  type LspMetrics,
  type MetricsTracker,
} from './lspMetrics'

export {
  createReconnectController,
  type ReconnectController,
  type ReconnectControllerOptions,
  type ReconnectStatus,
} from './reconnectController'

export {
  caseStatusToSeverity,
  casesToMarkers,
  defaultMessageForStatus,
} from './caseMarkers'
