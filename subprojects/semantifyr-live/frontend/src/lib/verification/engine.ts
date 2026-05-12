/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
  VerificationStatus,
  type Location,
  type VerificationCaseResult,
  type VerificationCaseSpecification,
  type VerificationMetrics,
  type VerificationTrace,
} from '@semantifyr/editor-common'
import {
  CancellationTokenSource as VscodeCancellationTokenSource,
  type CancellationToken as VscodeCancellationToken,
} from 'vscode-jsonrpc'

// Live-editor alias for the LSP `Location` shape so call sites that "navigate to case" or
// "verify here" read by their domain intent rather than the LSP primitive.
export type VerificationCaseLocation = Location

export type {
  CallTrace,
  CallTraceStep,
  Location,
  Range,
  TraceArgument,
  TraceEntry,
  VerificationCaseRequest,
  VerificationCaseResult,
  VerificationCaseSpecification,
  VerificationMetrics,
  VerificationTrace,
  WitnessState,
  WitnessStateStep,
  WitnessStateValue,
} from '@semantifyr/editor-common'

export type VerificationCaseStatus =
  | 'stale'
  | 'queued'
  | 'running'
  | 'passed'
  | 'failed'
  | 'inconclusive'
  | 'not_supported'
  | 'errored'

export type WitnessValidationStatus =
  | 'valid'
  | 'invalid'
  | 'inconclusive'
  | 'errored'

export interface VerificationCaseState {
  caseInfo: VerificationCaseSpecification
  status: VerificationCaseStatus
  message?: string
  trace?: VerificationTrace
  /** End-state of the witness validation (post-verify). */
  witnessValidation?: WitnessValidationStatus
  /**
   * True between the moment a witness arrives and the moment the follow-up validateWitness call
   * resolves. The witness panel uses it to render a "validating..." chip without blocking the
   * trace from showing immediately.
   */
  validating?: boolean
  /** Stage breakdown for the verify run that produced this verdict. */
  metrics?: VerificationMetrics
  /** Engine that produced the verify verdict (e.g. internal backend id). */
  backendId?: string
  /** Portfolio id the user picked for the verify run; recorded so the witness pane can label it. */
  portfolioId?: string
  /** Stage breakdown for the witness validation, parallel to {@link metrics}. */
  validationMetrics?: VerificationMetrics
  /** Engine that produced the validation verdict. */
  validationBackendId?: string
  /** Portfolio id used for the validation. */
  validationPortfolioIdUsed?: string
}

export type VerificationPhase = 'idle' | 'running' | 'done' | 'error' | 'cancelled'

export interface VerificationState {
  phase: VerificationPhase
  message?: string
  progress?: string
  cases: VerificationCaseState[]
}

// Reuse the LSP runtime's own cancellation primitives so the language client recognises the
// token via the standard `CancellationToken.is(...)` check and dispatches `$/cancelRequest`
// against the in-flight request id when the source is cancelled.
export type CancellationToken = VscodeCancellationToken
export type CancellationTokenSource = VscodeCancellationTokenSource
export const CancellationTokenSourceCtor = VscodeCancellationTokenSource

export function createCancellationTokenSource(): CancellationTokenSource {
  return new VscodeCancellationTokenSource()
}

export interface LspClient {
  sendRequest: (method: string, params?: unknown, token?: CancellationToken) => Promise<unknown>
  sendNotification: (method: string, params?: unknown) => void
}

interface ProgressParams {
  token: string | number
  value: {
    kind: 'begin' | 'report' | 'end'
    title?: string
    message?: string
    percentage?: number
  }
}

type StateUpdater = (next: VerificationState | ((prev: VerificationState) => VerificationState)) => void

function clearedRunningCase(cs: VerificationCaseState, portfolioId?: string): VerificationCaseState {
  // Drop trace + witnessValidation + per-run ids when a case starts running again.
  // exactOptionalPropertyTypes forbids assigning undefined directly, so build a fresh object
  // without the optional fields.
  return {
    caseInfo: cs.caseInfo,
    status: 'running',
    ...(cs.message !== undefined ? { message: cs.message } : {}),
    ...(portfolioId !== undefined ? { portfolioId } : {}),
  }
}

function parseResultStatus(response: VerificationCaseResult | null): VerificationCaseStatus {
  if (!response?.status) {
    return 'errored'
  }
  switch (response.status) {
    case VerificationStatus.Passed:
    case VerificationStatus.Failed:
    case VerificationStatus.Inconclusive:
    case VerificationStatus.NotSupported:
      return response.status
    default:
      return 'errored'
  }
}

/**
 * Wire shape produced by the OXSTS LSP's `oxsts.case.validateWitness` handler (see
 * `lang-ide-common/.../wire/WitnessValidationResult.java`). The handler maps the underlying
 * verify verdict to one of the four witness-validation kinds, then attaches the verifier's
 * regular {@link VerificationMetrics} stage breakdown plus the backend / portfolio ids.
 */
interface WitnessValidationResultResponse {
  status?: WitnessValidationStatus | string
  message?: string | null
  metrics?: VerificationMetrics | null
  backendId?: string | null
  portfolioId?: string | null
}

function parseValidationStatus(response: WitnessValidationResultResponse | null): WitnessValidationStatus {
  switch (response?.status) {
    case 'valid':
    case 'invalid':
    case 'inconclusive':
      return response.status
    default:
      return 'errored'
  }
}

export { discoverVerificationCases } from '@semantifyr/editor-common'

export interface RunVerificationHandle {
  cancel: () => void
}

export interface AutoValidateRequest {
  /** LSP command to fire for the witness; usually `<lang>.case.validateWitness`. */
  command: string
  /** Portfolio id to send with the validate request. */
  portfolioId?: string | undefined
}

export interface RunVerificationOptions {
  portfolioId?: string | undefined
  /**
   * Called after each verify response that produced a witness. Returning a non-null
   * {@link AutoValidateRequest} dispatches a follow-up validateWitness command. The runner
   * calls this fresh per case so toggling auto-validate mid-batch takes effect immediately.
   */
  getAutoValidateRequest?: (() => AutoValidateRequest | null) | undefined
}

function buildVerifyArguments(
  fileUri: string,
  caseInfo: VerificationCaseSpecification,
  options: RunVerificationOptions,
): Record<string, unknown> {
  const payload: Record<string, unknown> = {
    uri: fileUri,
    range: caseInfo.location.range,
    // Echoed back to the live-server so its active-verifications monitor can label the row
    // with a human-readable case name; the language server's command handlers ignore it.
    caseLabel: caseInfo.label,
  }
  if (options.portfolioId) {
    payload.portfolio = options.portfolioId
  }
  return payload
}

interface VerifyOutcome {
  status: VerificationCaseStatus
  message?: string
  trace?: VerificationTrace
  metrics?: VerificationMetrics
  backendId?: string
}

function toVerifyOutcome(response: VerificationCaseResult | null): VerifyOutcome {
  return {
    status: parseResultStatus(response),
    ...(response?.message != null ? { message: response.message } : {}),
    ...(response?.trace ? { trace: response.trace } : {}),
    ...(response?.metrics ? { metrics: response.metrics } : {}),
    ...(response?.backendId ? { backendId: response.backendId } : {}),
  }
}

function applyVerifyOutcome(cs: VerificationCaseState, outcome: VerifyOutcome): VerificationCaseState {
  return {
    ...cs,
    status: outcome.status,
    ...(outcome.message != null ? { message: outcome.message } : {}),
    ...(outcome.trace ? { trace: outcome.trace } : {}),
    ...(outcome.metrics ? { metrics: outcome.metrics } : {}),
    ...(outcome.backendId ? { backendId: outcome.backendId } : {}),
  }
}

async function executeVerify(
  client: LspClient,
  command: string,
  fileUri: string,
  caseInfo: VerificationCaseSpecification,
  options: RunVerificationOptions,
  token: CancellationToken,
): Promise<VerifyOutcome> {
  try {
    const response = (await client.sendRequest(
      'workspace/executeCommand',
      { command, arguments: [buildVerifyArguments(fileUri, caseInfo, options)] },
      token,
    )) as VerificationCaseResult | null
    return toVerifyOutcome(response)
  } catch (error) {
    return {
      status: 'errored',
      message: error instanceof Error ? error.message : String(error),
    }
  }
}

function maybeDispatchAutoValidation(
  client: LspClient,
  caseInfo: VerificationCaseSpecification,
  outcome: VerifyOutcome,
  setState: StateUpdater,
  options: RunVerificationOptions,
): void {
  const witnessUri = outcome.trace?.witnessUri
  if (!witnessUri) {
    return
  }
  const validate = options.getAutoValidateRequest?.()
  if (!validate) {
    return
  }
  dispatchAutoValidation(client, validate.command, caseInfo.id, witnessUri, setState, {
    validationPortfolioId: validate.portfolioId,
    caseLabel: caseInfo.label,
  })
}

function deriveBatchMessage(cases: readonly VerificationCaseState[]): string {
  const hasFailures = cases.some((cs) => cs.status === 'failed')
  const hasErrors = cases.some((cs) => cs.status === 'errored')
  const allPassed = cases.every((cs) => cs.status === 'passed')
  if (allPassed) {
    return 'All cases passed'
  }
  if (hasErrors && hasFailures) {
    return 'Some cases failed or errored'
  }
  if (hasErrors) {
    return 'Some cases errored'
  }
  return 'Some cases failed'
}

interface ProgressDispatcher {
  handle: (params: unknown) => void
  cancelAll: (client: LspClient) => void
}

function progressTextFor(value: ProgressParams['value']): string | undefined {
  if (value.kind === 'begin') {
    if (value.title && value.message) {
      return `${value.title}: ${value.message}`
    }
    return value.title ?? value.message
  }
  if (value.kind === 'report') {
    return value.message
  }
  return undefined
}

function createProgressDispatcher(setState: StateUpdater): ProgressDispatcher {
  const tokens = new Set<string | number>()
  return {
    handle: (params: unknown) => {
      const { token, value } = params as ProgressParams
      if (value.kind === 'begin') {
        tokens.add(token)
      } else if (value.kind === 'end') {
        tokens.delete(token)
        return
      }
      const text = progressTextFor(value)
      if (text) {
        setState((prev) => ({ ...prev, progress: text }))
      }
    },
    cancelAll: (client) => {
      for (const token of tokens) {
        try {
          client.sendNotification('window/workDoneProgress/cancel', { token })
        } catch {
          /* ignore */
        }
      }
    },
  }
}

export function runAllVerifications(
  client: LspClient,
  verificationCommand: string,
  fileUri: string,
  cases: readonly VerificationCaseSpecification[],
  setState: StateUpdater,
  onProgress: (listener: (params: unknown) => void) => () => void,
  options: RunVerificationOptions = {},
): RunVerificationHandle {
  let cancelled = false
  let activeRequestSource: CancellationTokenSource | null = null
  const progress = createProgressDispatcher(setState)

  const cancelHandle = (): void => {
    if (cancelled) {
      return
    }
    cancelled = true
    activeRequestSource?.cancel()
    progress.cancelAll(client)
    // Sweep the queue so the user doesn't see "queued" rows hanging around after cancel:
    // every case that's still queued or running collapses back to stale. The verifier's late
    // error response (if it arrives) updates the running case to a real verdict and overrides
    // this stale state.
    setState((prev) => ({
      ...prev,
      phase: 'cancelled',
      message: 'Verification cancelled',
      cases: prev.cases.map((cs) =>
        cs.status === 'queued' || cs.status === 'running'
          ? { caseInfo: cs.caseInfo, status: 'stale' as const }
          : cs,
      ),
    }))
  }

  const removeListener = onProgress(progress.handle)

  setState({
    phase: 'running',
    message: 'Starting verification...',
    cases: cases.map((c) => ({ caseInfo: c, status: 'queued' as const })),
  })

  const runOne = async (index: number): Promise<void> => {
    const currentCase = cases[index]!
    setState((prev) => ({
      ...prev,
      message: `Verifying ${currentCase.label} (${index + 1}/${cases.length})...`,
      cases: prev.cases.map((cs, idx) =>
        idx === index ? clearedRunningCase(cs, options.portfolioId) : cs,
      ),
    }))

    const requestSource = createCancellationTokenSource()
    activeRequestSource = requestSource
    let outcome: VerifyOutcome
    try {
      outcome = await executeVerify(
        client,
        verificationCommand,
        fileUri,
        currentCase,
        options,
        requestSource.token,
      )
    } finally {
      requestSource.dispose()
      if (activeRequestSource === requestSource) {
        activeRequestSource = null
      }
    }
    if (cancelled) {
      // The cancel handler already reset queue rows to stale; don't overwrite that with
      // a (likely error / no-response) verdict that arrives after the user gave up.
      return
    }

    setState((prev) => ({
      ...prev,
      cases: prev.cases.map((cs, idx) => (idx === index ? applyVerifyOutcome(cs, outcome) : cs)),
    }))

    maybeDispatchAutoValidation(client, currentCase, outcome, setState, options)
  }

  const runAll = async (): Promise<void> => {
    try {
      for (let i = 0; i < cases.length; i++) {
        if (cancelled) {
          setState((prev) => ({ ...prev, phase: 'cancelled', message: 'Verification cancelled' }))
          return
        }
        await runOne(i)
      }
      setState((prev) => ({ ...prev, phase: 'done', message: deriveBatchMessage(prev.cases) }))
    } catch (error) {
      if (cancelled) {
        setState((prev) => ({ ...prev, phase: 'cancelled', message: 'Verification cancelled' }))
      } else {
        setState((prev) => ({
          ...prev,
          phase: 'error',
          message: error instanceof Error ? error.message : String(error),
        }))
      }
    } finally {
      removeListener()
    }
  }

  void runAll()

  return { cancel: cancelHandle }
}

/**
 * Single-case variant that mirrors the batch flow's cancel + auto-validate semantics so the
 * panel's Cancel button reaches both flows uniformly. The handle's cancel fires both
 * `$/cancelRequest` (via the cancellation token, so the LSP can abort the running verifier
 * without waiting for the progress poll) AND `window/workDoneProgress/cancel` for any active
 * progress token, then resets the case to stale so the UI doesn't pretend it's still running.
 */
export function verifySingleCase(
  client: LspClient,
  verificationCommand: string,
  fileUri: string,
  targetCase: VerificationCaseSpecification,
  setState: StateUpdater,
  onProgress: (listener: (params: unknown) => void) => () => void,
  options: RunVerificationOptions = {},
): RunVerificationHandle {
  const caseId = targetCase.id
  let cancelled = false
  const requestSource = createCancellationTokenSource()
  const progress = createProgressDispatcher(setState)
  const removeListener = onProgress(progress.handle)

  const cancelHandle = (): void => {
    if (cancelled) {
      return
    }
    cancelled = true
    requestSource.cancel()
    progress.cancelAll(client)
    setState((prev) => ({
      ...prev,
      phase: 'cancelled',
      message: 'Verification cancelled',
      cases: prev.cases.map((cs) =>
        cs.caseInfo.id === caseId && (cs.status === 'running' || cs.status === 'queued')
          ? { caseInfo: cs.caseInfo, status: 'stale' as const }
          : cs,
      ),
    }))
  }

  setState((prev) => ({
    ...prev,
    phase: 'running',
    message: `Verifying ${targetCase.label}...`,
    cases: prev.cases.map((caseState) =>
      caseState.caseInfo.id === caseId ? clearedRunningCase(caseState, options.portfolioId) : caseState,
    ),
  }))

  void (async () => {
    try {
      const outcome = await executeVerify(
        client,
        verificationCommand,
        fileUri,
        targetCase,
        options,
        requestSource.token,
      )
      if (cancelled) {
        return
      }
      setState((prev) => ({
        ...prev,
        phase: 'done',
        cases: prev.cases.map((cs) =>
          cs.caseInfo.id === caseId ? applyVerifyOutcome(cs, outcome) : cs,
        ),
      }))
      maybeDispatchAutoValidation(client, targetCase, outcome, setState, options)
    } finally {
      requestSource.dispose()
      removeListener()
    }
  })()

  return { cancel: cancelHandle }
}

export interface ValidateWitnessLocation {
  uri: string
  range: {
    start: { line: number; character: number }
    end: { line: number; character: number }
  }
}

export interface WitnessValidationOutcome {
  status: WitnessValidationStatus
  message?: string
  metrics?: VerificationMetrics
  backendId?: string
  portfolioId?: string
}

export async function validateWitness(
  client: LspClient,
  validateCommand: string,
  location: ValidateWitnessLocation,
  options: RunVerificationOptions & { caseLabel?: string | undefined } = {},
  token?: CancellationToken,
): Promise<WitnessValidationOutcome> {
  const args: Record<string, unknown> = {
    uri: location.uri,
    range: location.range,
  }
  if (options.portfolioId) {
    args.portfolio = options.portfolioId
  }
  if (options.caseLabel) {
    args.caseLabel = options.caseLabel
  }
  try {
    const params = { command: validateCommand, arguments: [args] }
    // Pass `token` through unconditionally; the metrics wrapper this client is wrapped with
    // gates the trailing token away when undefined, so lsp4j never sees a positional null.
    const response = (await client.sendRequest('workspace/executeCommand', params, token)) as WitnessValidationResultResponse | null
    return toValidationOutcome(response)
  } catch (error) {
    return {
      status: 'errored',
      message: error instanceof Error ? error.message : String(error),
    }
  }
}

function toValidationOutcome(response: WitnessValidationResultResponse | null): WitnessValidationOutcome {
  return {
    status: parseValidationStatus(response),
    ...(response?.message != null ? { message: response.message } : {}),
    ...(response?.metrics ? { metrics: response.metrics } : {}),
    ...(response?.backendId ? { backendId: response.backendId } : {}),
    ...(response?.portfolioId ? { portfolioId: response.portfolioId } : {}),
  }
}

function applyValidationOutcome(
  cs: VerificationCaseState,
  outcome: WitnessValidationOutcome,
): VerificationCaseState {
  return {
    ...cs,
    validating: false,
    witnessValidation: outcome.status,
    ...(outcome.metrics ? { validationMetrics: outcome.metrics } : {}),
    ...(outcome.backendId ? { validationBackendId: outcome.backendId } : {}),
    ...(outcome.portfolioId ? { validationPortfolioIdUsed: outcome.portfolioId } : {}),
  }
}

/**
 * Runs validateWitness in the background and folds the outcome into the matching case state.
 * Used right after a verify response with a witness to perform the validation as a
 * separate throttled job, so the user sees the trace immediately and the validating-then-result
 * chip transition happens without blocking witness rendering.
 */
export function dispatchAutoValidation(
  client: LspClient,
  validateCommand: string,
  caseId: string,
  witnessUri: string,
  setState: StateUpdater,
  options: { validationPortfolioId?: string | undefined; caseLabel?: string | undefined } = {},
): void {
  setState((prev) => ({
    ...prev,
    cases: prev.cases.map((cs) =>
      cs.caseInfo.id === caseId ? { ...cs, validating: true } : cs,
    ),
  }))
  void validateWitness(
    client,
    validateCommand,
    {
      uri: witnessUri,
      range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
    },
    {
      ...(options.validationPortfolioId ? { portfolioId: options.validationPortfolioId } : {}),
      ...(options.caseLabel ? { caseLabel: options.caseLabel } : {}),
    },
  ).then((outcome) => {
    setState((prev) => ({
      ...prev,
      cases: prev.cases.map((cs) =>
        cs.caseInfo.id === caseId ? applyValidationOutcome(cs, outcome) : cs,
      ),
    }))
  })
}
