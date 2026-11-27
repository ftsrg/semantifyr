/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
  caseOutcomeOf,
  validateWitness as requestWitnessValidation,
  VerificationStatus,
  type VerificationCaseResult,
  type VerificationCaseSpecification,
  type VerificationReport,
  type WitnessValidationLocation,
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

import {
  cancelledCase,
  ranDuringOf,
  type VerificationCaseState,
  type VerificationCaseStatus,
  type VerificationState,
} from './state'

export const DEFAULT_VALIDATION_PORTFOLIO_ID = 'smart'

// Reuse vscode-jsonrpc's tokens so the language client dispatches $/cancelRequest properly.
export type CancellationToken = VscodeCancellationToken
export type CancellationTokenSource = VscodeCancellationTokenSource

export function createCancellationTokenSource(): CancellationTokenSource {
  return new VscodeCancellationTokenSource()
}

export interface LspClient {
  sendRequest: (method: string, params?: unknown, token?: CancellationToken) => Promise<unknown>
  sendNotification: (method: string, params?: unknown) => Promise<void> | void
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

// a case held before it was sent keeps the time it was asked for
function runningCase(caseState: VerificationCaseState): VerificationCaseState {
  const { serverState: _serverState, runningSince: _runningSince, ranDuring: _ranDuring, cancelledAt: _cancelledAt, ...rest } = caseState
  const askedAt = caseState.status === 'queued' && caseState.askedAt !== undefined ? caseState.askedAt : Date.now()
  return { ...rest, status: 'running', editedDuringRun: false, askedAt }
}

function parseResultStatus(status: VerificationStatus): VerificationCaseStatus {
  switch (status) {
    case VerificationStatus.Passed:
    case VerificationStatus.Failed:
    case VerificationStatus.Inconclusive:
    case VerificationStatus.Timeout:
    case VerificationStatus.NotSupported:
    case VerificationStatus.Cancelled:
      return status
    default:
      return 'errored'
  }
}

export { discoverVerificationCases, witnessValidationLocation } from '@semantifyr/editor-common'

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

interface RunVerificationOptions {
  portfolioId?: string | undefined
  // Called per case after a witness arrives; returning non-null dispatches validateWitness.
  getAutoValidateRequest?: (() => AutoValidateRequest | null) | undefined
}

interface VerifyOutcome {
  status: VerificationCaseStatus
  message?: string
  portfolioId?: string
  report?: VerificationReport
  witnessUri?: string
  traceUri?: string
}

function toVerifyOutcome(response: VerificationCaseResult | null): VerifyOutcome {
  if (response === null) {
    return { status: 'errored' }
  }
  const { status, message, portfolioId } = caseOutcomeOf(response)
  return {
    status: parseResultStatus(status),
    ...(message != null ? { message } : {}),
    ...(portfolioId != null ? { portfolioId } : {}),
    ...(response.report ? { report: response.report } : {}),
    ...(response.witnessUri ? { witnessUri: response.witnessUri } : {}),
    ...(response.traceUri ? { traceUri: response.traceUri } : {}),
  }
}

function applyVerifyOutcome(
  caseState: VerificationCaseState,
  outcome: VerifyOutcome,
): VerificationCaseState {
  const { askedAt, runningSince } = caseState
  return {
    caseInfo: caseState.caseInfo,
    ...ranDuringOf(caseState, Date.now()),
    ...(askedAt !== undefined && runningSince !== undefined ? { waited: runningSince - askedAt } : {}),
    status: outcome.status,
    resultStatus: outcome.status,
    outdated: caseState.editedDuringRun === true,
    ...(outcome.message != null ? { message: outcome.message } : {}),
    ...(outcome.report ? { report: outcome.report } : {}),
    ...(outcome.witnessUri ? { witnessUri: outcome.witnessUri } : {}),
    ...(outcome.traceUri ? { traceUri: outcome.traceUri } : {}),
    ...(outcome.portfolioId !== undefined ? { portfolioId: outcome.portfolioId } : {}),
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
  // a cancelled case is over at once, whenever the server answers its cancel
  const cancelled = new Promise<VerifyOutcome>((resolve) => {
    token.onCancellationRequested(() => {
      resolve({ status: 'cancelled' })
    })
  })
  try {
    const response = verifyCase(
      client,
      command,
      { uri: fileUri, range: caseInfo.location.range },
      {
        ...(options.portfolioId ? { portfolio: options.portfolioId } : {}),
        token,
      },
    ).then(toVerifyOutcome)
    return await Promise.race([response, cancelled])
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
  const witnessUri = outcome.witnessUri
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
  return cases.every((caseState) => caseState.status === 'passed') ? 'All cases passed' : 'Not every case passed'
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
        // a client whose session went refuses to send
        try {
          void Promise.resolve(client.sendNotification('window/workDoneProgress/cancel', { token })).catch(() => {})
        } catch {
          /* ignore */
        }
      }
    },
  }
}

// every case is sent at once; the server's queue decides the order
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
  const requestSources = new Map<string, CancellationTokenSource>()
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
    requestSources.get(caseId)?.cancel()
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
    for (const requestSource of requestSources.values()) {
      requestSource.cancel()
    }
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
      message: `Verifying ${cases.length} cases...`,
      cases: cases.map((caseInfo) => {
        return runningCase({ ...existing.get(caseInfo.id), caseInfo, status: 'queued' })
      }),
    }
  })

  const runOne = async (currentCase: VerificationCaseSpecification): Promise<void> => {
    const requestSource = createCancellationTokenSource()
    requestSources.set(currentCase.id, requestSource)
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
      requestSources.delete(currentCase.id)
    }
    if (cancelled || skipped.has(currentCase.id)) {
      return
    }
    setState((previous) => {
      return {
        ...previous,
        cases: previous.cases.map((caseState) => {
          return caseState.caseInfo.id === currentCase.id ? applyVerifyOutcome(caseState, outcome) : caseState
        }),
      }
    })
    maybeDispatchAutoValidation(client, currentCase, outcome, setState, options)
  }

  const runAll = async (): Promise<void> => {
    try {
      await Promise.all(cases.map(runOne))
      setState((previous) => {
        return cancelled
          ? { ...previous, phase: 'cancelled', message: 'Verification cancelled', progress: undefined }
          : { ...previous, phase: 'done', message: deriveBatchMessage(previous.cases), progress: undefined }
      })
    } catch (error) {
      setState((previous) => ({
        ...previous,
        phase: cancelled ? 'cancelled' : 'error',
        message: cancelled ? 'Verification cancelled' : error instanceof Error ? error.message : String(error),
        progress: undefined,
      }))
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
          caseState.caseInfo.id === caseId ? applyVerifyOutcome(caseState, outcome) : caseState,
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

interface WitnessValidationOutcome {
  status: WitnessValidationStatus
  message?: string
  report?: VerificationReport
  portfolioId?: string
}

export async function validateWitness(
  client: LspClient,
  validateCommand: string,
  location: WitnessValidationLocation,
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
    ...(response?.report ? { report: response.report } : {}),
    ...(response?.portfolioId ? { portfolioId: response.portfolioId } : {}),
  }
}

function withoutValidationServerStateAndAskedAt(caseState: VerificationCaseState): VerificationCaseState {
  const { validationServerState: _validationServerState, validationAskedAt: _validationAskedAt, ...rest } = caseState
  return rest
}

function applyValidationOutcome(
  caseState: VerificationCaseState,
  outcome: WitnessValidationOutcome,
): VerificationCaseState {
  const { validationReport: _validationReport, ...rest } = withoutValidationServerStateAndAskedAt(caseState)
  return {
    ...rest,
    validating: false,
    witnessValidation: outcome.status,
    validationMessage: outcome.message,
    ...(outcome.report ? { validationReport: outcome.report } : {}),
    ...(outcome.portfolioId ? { validationPortfolioIdUsed: outcome.portfolioId } : {}),
  }
}

function askedForValidation(caseState: VerificationCaseState): VerificationCaseState {
  const { validationWaited: _validationWaited, ...rest } = withoutValidationServerStateAndAskedAt(caseState)
  return { ...rest, validating: true, validationAskedAt: Date.now() }
}

export function dispatchAutoValidation(
  client: LspClient,
  validateCommand: string,
  caseId: string,
  location: WitnessValidationLocation,
  setState: StateUpdater,
  options: { validationPortfolioId?: string | undefined } = {},
): void {
  setState((previous) => ({
    ...previous,
    cases: previous.cases.map((caseState) =>
      caseState.caseInfo.id === caseId ? askedForValidation(caseState) : caseState,
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
