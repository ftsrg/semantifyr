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
  type VerificationMetrics as WireVerificationMetrics,
  type VerificationTrace,
} from '@semantifyr/editor-common';

// Live-editor alias for the LSP `Location` shape so call sites that "navigate to case" or
// "verify here" read by their domain intent rather than the LSP primitive.
export type VerificationCaseLocation = Location;

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
  VerificationTrace,
  WitnessState,
  WitnessStateStep,
  WitnessStateValue,
} from '@semantifyr/editor-common';

export type VerificationCaseStatus =
  | 'stale'
  | 'queued'
  | 'running'
  | 'passed'
  | 'failed'
  | 'inconclusive'
  | 'not_supported'
  | 'errored';

export type WitnessValidationStatus = 'valid' | 'invalid' | 'inconclusive' | 'errored';

// UI-state extension of the wire metrics: tracks the optional cross-validation duration the live
// editor records when auto-validating a witness.
export interface VerificationMetrics extends WireVerificationMetrics {
  validationDuration?: string;
}

export interface VerificationCaseState {
  caseInfo: VerificationCaseSpecification;
  status: VerificationCaseStatus;
  message?: string;
  trace?: VerificationTrace;
  witnessValidation?: WitnessValidationStatus;
  /**
   * True between the moment a witness arrives and the moment the follow-up validateWitness call
   * resolves. Used by the witness panel to render a "validating..." chip without blocking the
   * trace from showing immediately.
   */
  validating?: boolean;
  metrics?: VerificationMetrics;
  backendId?: string;
  // Portfolio id the user selected when this verify started. Recorded at request-dispatch time
  // so the witness viewer can render "verified using <portfolio>" even if the selector has
  // moved on after this case finished.
  portfolioId?: string;
}

export type VerificationPhase = 'idle' | 'running' | 'done' | 'error' | 'cancelled';

export interface VerificationState {
  phase: VerificationPhase;
  message?: string;
  progress?: string;
  cases: VerificationCaseState[];
}

// Reuse the LSP runtime's own cancellation primitives so the language client recognises the
// token via the standard `CancellationToken.is(...)` check and dispatches `$/cancelRequest`
// against the in-flight request id when the source is cancelled.
import { CancellationTokenSource as VscodeCancellationTokenSource, type CancellationToken as VscodeCancellationToken } from 'vscode-jsonrpc';

export type CancellationToken = VscodeCancellationToken;
export type CancellationTokenSource = VscodeCancellationTokenSource;
export const CancellationTokenSourceCtor = VscodeCancellationTokenSource;

export function createCancellationTokenSource(): CancellationTokenSource {
  return new VscodeCancellationTokenSource();
}

export interface LspClient {
  sendRequest: (method: string, params?: unknown, token?: CancellationToken) => Promise<unknown>;
  sendNotification: (method: string, params?: unknown) => void;
}

interface ProgressParams {
  token: string | number;
  value: {
    kind: 'begin' | 'report' | 'end';
    title?: string;
    message?: string;
    percentage?: number;
  };
}

type StateUpdater = (next: VerificationState | ((prev: VerificationState) => VerificationState)) => void;

function clearedRunningCase(cs: VerificationCaseState, portfolioId?: string): VerificationCaseState {
  // Drop trace + witnessValidation when a case starts running again. exactOptionalPropertyTypes
  // forbids assigning undefined directly, so build a fresh object without the optional fields.
  return {
    caseInfo: cs.caseInfo,
    status: 'running',
    ...(cs.message !== undefined ? { message: cs.message } : {}),
    ...(portfolioId !== undefined ? { portfolioId } : {}),
  };
}

function parseResultStatus(response: VerificationCaseResult | null): VerificationCaseStatus {
  if (!response?.status) return 'errored';
  switch (response.status) {
    case VerificationStatus.Passed:
    case VerificationStatus.Failed:
    case VerificationStatus.Inconclusive:
    case VerificationStatus.NotSupported:
      return response.status;
    default:
      return 'errored';
  }
}

// Wire shape produced by the OXSTS LSP's `oxsts.case.validateWitness` handler, which wraps the
// engine-side `WitnessValidator`. The handler already maps the underlying verify verdict to a
// witness-validation status; the client takes it as-is.
interface WitnessValidationResultResponse {
  status?: WitnessValidationStatus | string;
  message?: string | null;
  metrics?: { totalDuration?: string } | null;
}

function parseValidationStatus(response: WitnessValidationResultResponse | null): WitnessValidationStatus {
  switch (response?.status) {
    case 'valid':
    case 'invalid':
    case 'inconclusive':
      return response.status;
    default:
      return 'errored';
  }
}

export { discoverVerificationCases } from '@semantifyr/editor-common';

export interface RunVerificationHandle {
  cancel: () => void;
}

export interface AutoValidateRequest {
  /** LSP command to fire for the witness; usually `<lang>.case.validateWitness`. */
  command: string;
  /** Portfolio id to send with the validate request. */
  portfolioId?: string | undefined;
}

export interface RunVerificationOptions {
  portfolioId?: string | undefined;
  /**
   * Called after each verify response that produced a witness. Returning a non-null
   * {@link AutoValidateRequest} dispatches a follow-up validateWitness command. The runner
   * calls this fresh per case so toggling auto-validate mid-batch takes effect immediately.
   */
  getAutoValidateRequest?: (() => AutoValidateRequest | null) | undefined;
}

function buildVerifyArguments(
  fileUri: string,
  caseInfo: VerificationCaseSpecification,
  options: RunVerificationOptions,
): Record<string, unknown> {
  const payload: Record<string, unknown> = {
    uri: fileUri,
    range: caseInfo.location.range,
    // Echoed back to the live-server bridge so its in-flight monitor can label the row with
    // a human-readable case name; the LSP child handlers ignore the field.
    caseLabel: caseInfo.label,
  };
  if (options.portfolioId) {
    payload.portfolio = options.portfolioId;
  }
  return payload;
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
  let cancelled = false;
  const activeProgressTokens = new Set<string | number>();
  // CancellationTokenSource for the in-flight verify request. Cancelling fires the language
  // client's `$/cancelRequest` for that request id, so the LSP can abort the running verifier
  // immediately rather than waiting for the per-case progress poll (which can lag while Theta
  // / Spin / etc. are mid-process).
  let activeRequestSource: CancellationTokenSource | null = null;

  const handle: RunVerificationHandle = {
    cancel: () => {
      if (cancelled) return;
      cancelled = true;
      activeRequestSource?.cancel();
      for (const token of activeProgressTokens) {
        try {
          client.sendNotification('window/workDoneProgress/cancel', { token });
        } catch {
          /* ignore */
        }
      }
      // Sweep the queue so the user doesn't see "queued" rows hanging around after cancel:
      // every case that's still queued or running collapses back to stale. The verifier's
      // late error response (if it arrives) updates the running case to a real verdict and
      // overrides this stale state.
      setState((prev) => ({
        ...prev,
        phase: 'cancelled',
        message: 'Verification cancelled',
        cases: prev.cases.map((cs) =>
          cs.status === 'queued' || cs.status === 'running'
            ? { caseInfo: cs.caseInfo, status: 'stale' as const }
            : cs,
        ),
      }));
    },
  };

  const progressListener = (params: unknown): void => {
    const { token, value } = params as ProgressParams;
    if (value.kind === 'begin') {
      activeProgressTokens.add(token);
      const text = value.title
        ? value.message
          ? `${value.title}: ${value.message}`
          : value.title
        : value.message;
      if (text) setState((prev) => ({ ...prev, progress: text }));
    } else if (value.kind === 'report' && value.message) {
      const progressText = value.message;
      setState((prev) => ({ ...prev, progress: progressText }));
    } else if (value.kind === 'end') {
      activeProgressTokens.delete(token);
    }
  };

  const removeListener = onProgress(progressListener);

  setState({
    phase: 'running',
    message: 'Starting verification...',
    cases: cases.map((c) => ({ caseInfo: c, status: 'queued' as const })),
  });

  const verificationStarter = async () => {
    try {
      for (let i = 0; i < cases.length; i++) {
        if (cancelled) {
          setState((prev) => ({ ...prev, phase: 'cancelled', message: 'Verification cancelled' }));
          return;
        }
        const currentCase = cases[i]!;

        setState((prev) => ({
          ...prev,
          message: `Verifying ${currentCase.label} (${i + 1}/${cases.length})...`,
          cases: prev.cases.map((cs, idx) =>
            idx === i ? clearedRunningCase(cs, options.portfolioId) : cs,
          ),
        }));

        let resultStatus: VerificationCaseStatus;
        let resultMessage: string | undefined;
        let trace: VerificationTrace | undefined;
        let metrics: VerificationMetrics | undefined;
        let backendId: string | undefined;
        const requestSource = createCancellationTokenSource();
        activeRequestSource = requestSource;
        try {
          const response = (await client.sendRequest(
            'workspace/executeCommand',
            {
              command: verificationCommand,
              arguments: [buildVerifyArguments(fileUri, currentCase, options)],
            },
            requestSource.token,
          )) as VerificationCaseResult | null;

          resultStatus = parseResultStatus(response);
          resultMessage = response?.message ?? undefined;
          trace = response?.trace ?? undefined;
          metrics = response?.metrics ?? undefined;
          backendId = response?.backendId ?? undefined;
        } catch (caseError) {
          resultStatus = 'errored';
          resultMessage = caseError instanceof Error ? caseError.message : String(caseError);
        } finally {
          requestSource.dispose();
          if (activeRequestSource === requestSource) {
            activeRequestSource = null;
          }
        }
        if (cancelled) {
          // The cancel handler already reset queue rows to stale; don't overwrite that with
          // a (likely error / no-response) verdict that arrives after the user gave up.
          return;
        }

        setState((prev) => ({
          ...prev,
          cases: prev.cases.map((cs, idx) =>
            idx === i
              ? {
                  ...cs,
                  status: resultStatus,
                  ...(resultMessage != null ? { message: resultMessage } : {}),
                  ...(trace ? { trace } : {}),
                  ...(metrics ? { metrics } : {}),
                  ...(backendId ? { backendId } : {}),
                }
              : cs,
          ),
        }));

        // Kick off the auto-validate as soon as the witness is visible; it runs in the
        // background and folds its result into the same case state when it returns. The
        // request is sourced fresh per case so the user toggling auto-validate mid-batch
        // (or switching the validation portfolio) takes effect on subsequent cases.
        if (trace?.witnessUri) {
          const validate = options.getAutoValidateRequest?.();
          if (validate) {
            dispatchAutoValidation(client, validate.command, currentCase.id, trace.witnessUri, setState, {
              validationPortfolioId: validate.portfolioId,
              caseLabel: currentCase.label,
            });
          }
        }
      }

      setState((prev) => {
        const hasFailures = prev.cases.some((cs) => cs.status === 'failed');
        const hasErrors = prev.cases.some((cs) => cs.status === 'errored');
        const allPassed = prev.cases.every((cs) => cs.status === 'passed');
        const message = allPassed
          ? 'All cases passed'
          : hasErrors && hasFailures
            ? 'Some cases failed or errored'
            : hasErrors
              ? 'Some cases errored'
              : 'Some cases failed';
        return { ...prev, phase: 'done', message };
      });
    } catch (error) {
      if (cancelled) {
        setState((prev) => ({ ...prev, phase: 'cancelled', message: 'Verification cancelled' }));
      } else {
        setState((prev) => ({
          ...prev,
          phase: 'error',
          message: error instanceof Error ? error.message : String(error),
        }));
      }
    } finally {
      removeListener();
    }
  };

  void verificationStarter();

  return handle;
}

/**
 * Returns a {@link RunVerificationHandle} so the caller can plumb the same Cancel button at
 * the bottom of the verification panel for both batch and single-case flows. The handle's
 * cancel both fires `$/cancelRequest` (so the LSP can abort the running verifier without
 * waiting for the progress poll) AND resets the case to stale so the UI doesn't pretend it's
 * still running.
 */
export function verifySingleCase(
  client: LspClient,
  verificationCommand: string,
  fileUri: string,
  targetCase: VerificationCaseSpecification,
  setState: StateUpdater,
  options: RunVerificationOptions = {},
): RunVerificationHandle {
  const caseId = targetCase.id;
  let cancelled = false;
  const requestSource = createCancellationTokenSource();

  const handle: RunVerificationHandle = {
    cancel: () => {
      if (cancelled) return;
      cancelled = true;
      requestSource.cancel();
      setState((prev) => ({
        ...prev,
        phase: 'cancelled',
        message: 'Verification cancelled',
        cases: prev.cases.map((cs) =>
          cs.caseInfo.id === caseId && (cs.status === 'running' || cs.status === 'queued')
            ? { caseInfo: cs.caseInfo, status: 'stale' as const }
            : cs,
        ),
      }));
    },
  };

  setState((prev) => ({
    ...prev,
    phase: 'running',
    message: `Verifying ${targetCase.label}...`,
    cases: prev.cases.map((caseState) =>
      caseState.caseInfo.id === caseId ? clearedRunningCase(caseState, options.portfolioId) : caseState,
    ),
  }));

  void (async () => {
    try {
      const response = (await client.sendRequest(
        'workspace/executeCommand',
        {
          command: verificationCommand,
          arguments: [buildVerifyArguments(fileUri, targetCase, options)],
        },
        requestSource.token,
      )) as VerificationCaseResult | null;

      if (cancelled) return;

      const resultStatus = parseResultStatus(response);
      const trace = response?.trace ?? undefined;
      const metrics = response?.metrics ?? undefined;
      const backendId = response?.backendId ?? undefined;

      setState((prev) => ({
        ...prev,
        phase: 'done',
        cases: prev.cases.map((cs) =>
          cs.caseInfo.id === caseId
            ? {
                ...cs,
                status: resultStatus,
                ...(response?.message != null ? { message: response.message } : {}),
                ...(trace ? { trace } : {}),
                ...(metrics ? { metrics } : {}),
                ...(backendId ? { backendId } : {}),
              }
            : cs,
        ),
      }));

      if (trace?.witnessUri) {
        const validate = options.getAutoValidateRequest?.();
        if (validate) {
          dispatchAutoValidation(client, validate.command, caseId, trace.witnessUri, setState, {
            validationPortfolioId: validate.portfolioId,
            caseLabel: targetCase.label,
          });
        }
      }
    } catch (error) {
      if (cancelled) return;
      setState((prev) => ({
        ...prev,
        phase: 'done',
        cases: prev.cases.map((cs) =>
          cs.caseInfo.id === caseId
            ? { ...cs, status: 'errored' as const, message: error instanceof Error ? error.message : String(error) }
            : cs,
        ),
      }));
    } finally {
      requestSource.dispose();
    }
  })();

  return handle;
}

export interface ValidateWitnessLocation {
  uri: string;
  range: {
    start: { line: number; character: number };
    end: { line: number; character: number };
  };
}

export interface WitnessValidationOutcome {
  status: WitnessValidationStatus;
  message?: string;
  durationIso?: string;
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
  };
  if (options.portfolioId) {
    args.portfolio = options.portfolioId;
  }
  if (options.caseLabel) {
    args.caseLabel = options.caseLabel;
  }
  try {
    const response = (token === undefined
      ? await client.sendRequest('workspace/executeCommand', {
          command: validateCommand,
          arguments: [args],
        })
      : await client.sendRequest('workspace/executeCommand', {
          command: validateCommand,
          arguments: [args],
        }, token)) as WitnessValidationResultResponse | null;
    const outcome: WitnessValidationOutcome = { status: parseValidationStatus(response) };
    if (response?.message != null) outcome.message = response.message;
    if (response?.metrics?.totalDuration != null) outcome.durationIso = response.metrics.totalDuration;
    return outcome;
  } catch (error) {
    return {
      status: 'errored',
      message: error instanceof Error ? error.message : String(error),
    };
  }
}

/**
 * Runs validateWitness in the background and folds the outcome into the matching case state.
 * Used right after a verify response with a witness to perform the cross-validation as a
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
  }));
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
      cases: prev.cases.map((cs) => {
        if (cs.caseInfo.id !== caseId) return cs;
        const next: VerificationCaseState = {
          ...cs,
          validating: false,
          witnessValidation: outcome.status,
        };
        if (outcome.durationIso && cs.metrics) {
          next.metrics = { ...cs.metrics, validationDuration: outcome.durationIso };
        }
        return next;
      }),
    }));
  });
}
