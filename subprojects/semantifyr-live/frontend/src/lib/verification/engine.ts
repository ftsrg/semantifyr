/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
  discoverVerificationCases,
  validateWitness as requestWitnessValidation,
  VerificationStatus,
  type Location,
  type VerificationCaseResult,
  type VerificationCaseSpecification,
  type VerificationMetrics,
  type VerificationTrace,
  type WitnessValidationResult,
  type WitnessValidationStatus,
  verifyCase,
  witnessValidationLocation,
  witnessValidationStatusOf,
} from '@semantifyr/editor-common'
import {
  CancellationTokenSource as VscodeCancellationTokenSource,
  type CancellationToken as VscodeCancellationToken,
} from 'vscode-jsonrpc'

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
  WitnessValidationStatus,
} from '@semantifyr/editor-common'

export const DEFAULT_VALIDATION_PORTFOLIO_ID = 'smart-full'

export type VerificationCaseStatus =
  | 'stale'
  | 'queued'
  | 'running'
  | 'passed'
  | 'failed'
  | 'inconclusive'
  | 'timeout'
  | 'not_supported'
  | 'errored'
  | 'cancelled'

export interface VerificationCaseState {
  caseInfo: VerificationCaseSpecification
  status: VerificationCaseStatus
  message?: string
  trace?: VerificationTrace
  witnessValidation?: WitnessValidationStatus
  validationMessage?: string | undefined
  // True between the trace arriving and the follow-up validateWitness resolving.
  validating?: boolean
  metrics?: VerificationMetrics
  backendId?: string
  portfolioId?: string
  validationMetrics?: VerificationMetrics
  validationBackendId?: string
  validationPortfolioIdUsed?: string
  // the status of the result shown, kept while a new run of the case is queued or running
  resultStatus?: VerificationCaseStatus
  // the model was edited after the result shown was verified
  outdated?: boolean
  editedDuringRun?: boolean
}

export type VerificationPhase = 'idle' | 'running' | 'done' | 'error' | 'cancelled'

export type DiscoveryPhase = 'idle' | 'discovering' | 'discovered'

export interface VerificationState {
  phase: VerificationPhase
  discovery: DiscoveryPhase
  message?: string
  progress?: string | undefined
  cases: VerificationCaseState[]
}

// Reuse vscode-jsonrpc's tokens so the language client dispatches $/cancelRequest properly.
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

type StateUpdater = (next: VerificationState | ((previous: VerificationState) => VerificationState)) => void

function runningCase(caseState: VerificationCaseState): VerificationCaseState {
  return { ...caseState, status: 'running', editedDuringRun: false }
}

function cancelledCase(caseState: VerificationCaseState): VerificationCaseState {
  if (caseState.resultStatus === undefined) {
    return { caseInfo: caseState.caseInfo, status: 'stale', message: 'Verification cancelled' }
  }
  const { editedDuringRun: _editedDuringRun, ...result } = caseState
  return { ...result, status: caseState.resultStatus }
}

export function markEdited(state: VerificationState): VerificationState {
  return {
    ...state,
    cases: state.cases.map((caseState) => {
      if (caseState.status === 'running') {
        return { ...caseState, editedDuringRun: true }
      }
      if (caseState.resultStatus !== undefined && caseState.outdated !== true) {
        return { ...caseState, outdated: true }
      }
      return caseState
    }),
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
    case VerificationStatus.Timeout:
    case VerificationStatus.NotSupported:
    case VerificationStatus.Cancelled:
      return response.status
    default:
      return 'errored'
  }
}

export { discoverVerificationCases, witnessValidationLocation } from '@semantifyr/editor-common'

export function mergeDiscoveredCases(
  previous: readonly VerificationCaseState[],
  discovered: readonly VerificationCaseSpecification[],
): VerificationCaseState[] {
  const existingById = new Map(previous.map((caseState) => [caseState.caseInfo.id, caseState]))
  return discovered.map((caseInfo) => {
    const existing = existingById.get(caseInfo.id)
    if (existing) {
      return { ...existing, caseInfo }
    }
    return { caseInfo, status: 'stale' as const }
  })
}

export async function discoverCases(
  client: LspClient,
  discoveryCommand: string,
  fileUri: string,
  setState: StateUpdater,
): Promise<boolean> {
  setState((previous) => ({ ...previous, discovery: 'discovering' }))
  let discovered: VerificationCaseSpecification[]
  try {
    discovered = await discoverVerificationCases(client, discoveryCommand, fileUri)
  } catch {
    setState((previous) => ({ ...previous, discovery: 'idle' }))
    return false
  }
  setState((previous) => {
    if (previous.phase === 'running') {
      return { ...previous, discovery: 'discovered' }
    }
    return { ...previous, discovery: 'discovered', cases: mergeDiscoveredCases(previous.cases, discovered) }
  })
  return true
}

export interface RunVerificationHandle {
  cancel: () => void
  // cancels one running or queued case and lets the rest of the run go on
  cancelCase: (caseId: string) => void
  done: Promise<void>
}

export interface AutoValidateRequest {
  command: string
  portfolioId?: string | undefined
  byCase?: boolean | undefined
}

export interface RunVerificationOptions {
  portfolioId?: string | undefined
  // Called per case after a witness arrives; returning non-null dispatches validateWitness.
  getAutoValidateRequest?: (() => AutoValidateRequest | null) | undefined
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

function applyVerifyOutcome(
  caseState: VerificationCaseState,
  outcome: VerifyOutcome,
  portfolioId: string | undefined,
): VerificationCaseState {
  return {
    caseInfo: caseState.caseInfo,
    status: outcome.status,
    resultStatus: outcome.status,
    outdated: caseState.editedDuringRun === true,
    ...(outcome.message != null ? { message: outcome.message } : {}),
    ...(outcome.trace ? { trace: outcome.trace } : {}),
    ...(outcome.metrics ? { metrics: outcome.metrics } : {}),
    ...(outcome.backendId ? { backendId: outcome.backendId } : {}),
    ...(portfolioId !== undefined ? { portfolioId } : {}),
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
    const response = await verifyCase(
      client,
      command,
      { uri: fileUri, range: caseInfo.location.range },
      {
        ...(options.portfolioId ? { portfolio: options.portfolioId } : {}),
        token,
      },
    )
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
  const location = witnessValidationLocation(caseInfo.location, witnessUri, validate.byCase ?? false)
  dispatchAutoValidation(client, validate.command, caseInfo.id, location, setState, {
    validationPortfolioId: validate.portfolioId,
  })
}

const CASE_OUTCOME_WORDS: Partial<Record<VerificationCaseStatus, string>> = {
  passed: 'passed',
  failed: 'failed',
  inconclusive: 'was inconclusive',
  timeout: 'timed out',
  not_supported: 'is not supported by the portfolio',
  errored: 'errored',
  cancelled: 'was cancelled',
}

function deriveCaseMessage(caseState: VerificationCaseState | undefined): string | undefined {
  if (caseState === undefined) {
    return undefined
  }
  const word = CASE_OUTCOME_WORDS[caseState.status]
  return word === undefined ? undefined : `${caseState.caseInfo.label} ${word}`
}

function deriveBatchMessage(cases: readonly VerificationCaseState[]): string {
  const hasFailures = cases.some((caseState) => caseState.status === 'failed')
  const hasErrors = cases.some((caseState) => caseState.status === 'errored' || caseState.status === 'timeout')
  const allPassed = cases.every((caseState) => caseState.status === 'passed')
  if (allPassed) {
    return 'All cases passed'
  }
  if (hasErrors && hasFailures) {
    return 'Some cases failed, errored or timed out'
  }
  if (hasErrors) {
    return 'Some cases errored or timed out'
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
        setState((previous) => ({ ...previous, progress: text }))
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
  let activeCaseId: string | null = null
  const skipped = new Set<string>()
  const progress = createProgressDispatcher(setState)
  const caseIds = new Set(cases.map((caseInfo) => {
    return caseInfo.id
  }))

  const cancelOne = (caseId: string): void => {
    if (cancelled || !caseIds.has(caseId) || skipped.has(caseId)) {
      return
    }
    skipped.add(caseId)
    if (activeCaseId === caseId) {
      activeRequestSource?.cancel()
    }
    setState((previous) => ({
      ...previous,
      cases: previous.cases.map((caseState) =>
        caseState.caseInfo.id === caseId && (caseState.status === 'queued' || caseState.status === 'running')
          ? cancelledCase(caseState)
          : caseState,
      ),
    }))
  }

  const cancelHandle = (): void => {
    if (cancelled) {
      return
    }
    cancelled = true
    activeRequestSource?.cancel()
    progress.cancelAll(client)
    setState((previous) => ({
      ...previous,
      phase: 'cancelled',
      message: 'Verification cancelled',
      progress: undefined,
      cases: previous.cases.map((caseState) =>
        caseIds.has(caseState.caseInfo.id) && (caseState.status === 'queued' || caseState.status === 'running')
          ? cancelledCase(caseState)
          : caseState,
      ),
    }))
  }

  const removeListener = onProgress(progress.handle)

  setState((previous) => {
    const existing = new Map(previous.cases.map((caseState) => {
      return [caseState.caseInfo.id, caseState]
    }))
    return {
      phase: 'running',
      discovery: previous.discovery,
      message: 'Starting verification...',
      cases: cases.map((caseInfo) => {
        return { ...existing.get(caseInfo.id), caseInfo, status: 'queued' as const }
      }),
    }
  })

  const updateCase = (caseId: string, change: (caseState: VerificationCaseState) => VerificationCaseState): void => {
    setState((previous) => {
      return {
        ...previous,
        cases: previous.cases.map((caseState) => {
          return caseState.caseInfo.id === caseId ? change(caseState) : caseState
        }),
      }
    })
  }

  const runOne = async (index: number): Promise<void> => {
    const currentCase = cases[index]
    if (!currentCase || skipped.has(currentCase.id)) {
      return
    }
    setState((previous) => {
      return {
        ...previous,
        message: `Verifying ${currentCase.label} (${index + 1}/${cases.length})...`,
        cases: previous.cases.map((caseState) => {
          return caseState.caseInfo.id === currentCase.id ? runningCase(caseState) : caseState
        }),
      }
    })

    const requestSource = createCancellationTokenSource()
    activeRequestSource = requestSource
    activeCaseId = currentCase.id
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
        activeCaseId = null
      }
    }
    if (cancelled || skipped.has(currentCase.id)) {
      return
    }

    updateCase(currentCase.id, (caseState) => {
      return applyVerifyOutcome(caseState, outcome, options.portfolioId)
    })

    maybeDispatchAutoValidation(client, currentCase, outcome, setState, options)
  }

  const runAll = async (): Promise<void> => {
    try {
      for (let i = 0; i < cases.length; i++) {
        if (cancelled) {
          setState((previous) => {
            return { ...previous, phase: 'cancelled', message: 'Verification cancelled', progress: undefined }
          })
          return
        }
        await runOne(i)
      }
      if (!cancelled) {
        setState((previous) => {
          return { ...previous, phase: 'done', message: deriveBatchMessage(previous.cases), progress: undefined }
        })
      }
    } catch (error) {
      if (cancelled) {
        setState((previous) => {
          return { ...previous, phase: 'cancelled', message: 'Verification cancelled', progress: undefined }
        })
      } else {
        setState((previous) => ({
          ...previous,
          phase: 'error',
          message: error instanceof Error ? error.message : String(error),
          progress: undefined,
        }))
      }
    } finally {
      removeListener()
    }
  }

  return { cancel: cancelHandle, cancelCase: cancelOne, done: runAll() }
}

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
    setState((previous) => ({
      ...previous,
      phase: 'cancelled',
      message: 'Verification cancelled',
      progress: undefined,
      cases: previous.cases.map((caseState) =>
        caseState.caseInfo.id === caseId && (caseState.status === 'running' || caseState.status === 'queued')
          ? cancelledCase(caseState)
          : caseState,
      ),
    }))
  }

  setState((previous) => ({
    ...previous,
    phase: 'running',
    message: `Verifying ${targetCase.label}...`,
    cases: previous.cases.map((caseState) =>
      caseState.caseInfo.id === caseId ? runningCase(caseState) : caseState,
    ),
  }))

  const run = async (): Promise<void> => {
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
      setState((previous) => {
        const cases = previous.cases.map((caseState) =>
          caseState.caseInfo.id === caseId ? applyVerifyOutcome(caseState, outcome, options.portfolioId) : caseState,
        )
        const message = deriveCaseMessage(cases.find((caseState) => {
          return caseState.caseInfo.id === caseId
        }))
        return {
          ...previous,
          phase: 'done',
          progress: undefined,
          ...(message !== undefined ? { message } : {}),
          cases,
        }
      })
      maybeDispatchAutoValidation(client, targetCase, outcome, setState, options)
    } finally {
      requestSource.dispose()
      removeListener()
    }
  }

  return {
    cancel: cancelHandle,
    cancelCase: (cancelledId) => {
      if (cancelledId === caseId) {
        cancelHandle()
      }
    },
    done: run(),
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
  location: VerificationCaseLocation,
  options: RunVerificationOptions = {},
  token?: CancellationToken,
): Promise<WitnessValidationOutcome> {
  try {
    const response = await requestWitnessValidation(client, validateCommand, location, {
      ...(options.portfolioId ? { portfolio: options.portfolioId } : {}),
      ...(token ? { token } : {}),
    })
    return toValidationOutcome(response)
  } catch (error) {
    return {
      status: 'errored',
      message: error instanceof Error ? error.message : String(error),
    }
  }
}

function toValidationOutcome(response: WitnessValidationResult | null): WitnessValidationOutcome {
  return {
    status: witnessValidationStatusOf(response),
    ...(response?.message != null ? { message: response.message } : {}),
    ...(response?.metrics ? { metrics: response.metrics } : {}),
    ...(response?.backendId ? { backendId: response.backendId } : {}),
    ...(response?.portfolioId ? { portfolioId: response.portfolioId } : {}),
  }
}

function applyValidationOutcome(
  caseState: VerificationCaseState,
  outcome: WitnessValidationOutcome,
): VerificationCaseState {
  return {
    ...caseState,
    validating: false,
    witnessValidation: outcome.status,
    validationMessage: outcome.message,
    ...(outcome.metrics ? { validationMetrics: outcome.metrics } : {}),
    ...(outcome.backendId ? { validationBackendId: outcome.backendId } : {}),
    ...(outcome.portfolioId ? { validationPortfolioIdUsed: outcome.portfolioId } : {}),
  }
}

export function dispatchAutoValidation(
  client: LspClient,
  validateCommand: string,
  caseId: string,
  location: VerificationCaseLocation,
  setState: StateUpdater,
  options: { validationPortfolioId?: string | undefined } = {},
): void {
  setState((previous) => ({
    ...previous,
    cases: previous.cases.map((caseState) =>
      caseState.caseInfo.id === caseId ? { ...caseState, validating: true } : caseState,
    ),
  }))
  void validateWitness(
    client,
    validateCommand,
    location,
    {
      ...(options.validationPortfolioId ? { portfolioId: options.validationPortfolioId } : {}),
    },
  ).then((outcome) => {
    setState((previous) => ({
      ...previous,
      cases: previous.cases.map((caseState) =>
        caseState.caseInfo.id === caseId ? applyValidationOutcome(caseState, outcome) : caseState,
      ),
    }))
  })
}
