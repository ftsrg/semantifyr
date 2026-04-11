/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface VerificationCaseLocation {
  uri: string;
  range: {
    start: { line: number; character: number };
    end: { line: number; character: number };
  };
}

export interface VerificationCaseInfo {
  id: string;
  label: string;
  location: VerificationCaseLocation;
}

export type VerificationCaseStatus = 'stale' | 'queued' | 'running' | 'passed' | 'failed' | 'errored';

export interface VerificationCaseState {
  caseInfo: VerificationCaseInfo;
  status: VerificationCaseStatus;
  message?: string;
}

export type VerificationPhase = 'idle' | 'running' | 'done' | 'error' | 'cancelled';

export interface VerificationState {
  phase: VerificationPhase;
  message?: string;
  progress?: string;
  cases: VerificationCaseState[];
}

export interface LspClient {
  sendRequest: (method: string, params?: unknown) => Promise<unknown>;
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

interface VerificationResponse {
  status?: string;
  message?: string;
}

function parseResultStatus(response: VerificationResponse | null): VerificationCaseStatus {
  if (!response?.status) return 'errored';
  switch (response.status) {
    case 'passed': return 'passed';
    case 'failed': return 'failed';
    default: return 'errored';
  }
}

export async function discoverVerificationCases(
  client: LspClient,
  fileUri: string,
): Promise<VerificationCaseInfo[]> {
  const result = await client.sendRequest('workspace/executeCommand', {
    command: 'oxsts.case.discover',
    arguments: [fileUri],
  });
  return result as VerificationCaseInfo[] ?? [];
}

export interface RunVerificationHandle {
  cancel: () => void;
}

export function runVerifyAll(
  client: LspClient,
  verifyCommand: string,
  fileUri: string,
  cases: readonly VerificationCaseInfo[],
  setState: StateUpdater,
  onProgress: (listener: (params: unknown) => void) => () => void,
): RunVerificationHandle {
  let cancelled = false;
  const activeProgressTokens = new Set<string | number>();

  const handle: RunVerificationHandle = {
    cancel: () => {
      if (cancelled) return;
      cancelled = true;
      for (const token of activeProgressTokens) {
        try { client.sendNotification('window/workDoneProgress/cancel', { token }); }
        catch { /* ignore */ }
      }
    },
  };

  const progressListener = (params: unknown): void => {
    const { token, value } = params as ProgressParams;
    if (value.kind === 'begin') {
      activeProgressTokens.add(token);
      const text = value.title
        ? value.message ? `${value.title}: ${value.message}` : value.title
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
            idx === i ? { ...cs, status: 'running' as const } : cs
          ),
        }));

        let resultStatus: VerificationCaseStatus;
        let resultMessage: string | undefined;
        try {
          const response = (await client.sendRequest('workspace/executeCommand', {
            command: verifyCommand,
            arguments: [{ uri: fileUri, range: currentCase.location.range }],
          })) as VerificationResponse | null;

          resultStatus = parseResultStatus(response);
          resultMessage = response?.message ?? undefined;
        } catch (caseError) {
          resultStatus = 'errored';
          resultMessage = caseError instanceof Error ? caseError.message : String(caseError);
        }

        setState((prev) => ({
          ...prev,
          cases: prev.cases.map((cs, idx) =>
            idx === i
              ? { ...cs, status: resultStatus, ...(resultMessage != null ? { message: resultMessage } : {}) }
              : cs
          ),
        }));
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

  void verificationStarter()

  return handle;
}

export async function verifySingleCase(
  client: LspClient,
  verificationCommand: string,
  fileUri: string,
  targetCase: VerificationCaseInfo,
  setState: StateUpdater,
): Promise<void> {
  const caseId = targetCase.id;

  setState((prev) => ({
    ...prev,
    phase: 'running',
    message: `Verifying ${targetCase.label}...`,
    cases: prev.cases.map((caseState) =>
      caseState.caseInfo.id === caseId ? { ...caseState, status: 'running' as const } : caseState
    ),
  }));

  try {
    const response = await client.sendRequest('workspace/executeCommand', {
      command: verificationCommand,
      arguments: [{ uri: fileUri, range: targetCase.location.range }],
    }) as VerificationResponse | null;

    const resultStatus = parseResultStatus(response);

    setState((prev) => ({
      ...prev,
      phase: 'done',
      cases: prev.cases.map((cs) =>
        cs.caseInfo.id === caseId
          ? { ...cs, status: resultStatus, ...(response?.message != null ? { message: response.message } : {}) }
          : cs
      ),
    }));
  } catch (error) {
    setState((prev) => ({
      ...prev,
      phase: 'done',
      cases: prev.cases.map((cs) =>
        cs.caseInfo.id === caseId
          ? { ...cs, status: 'errored' as const, message: error instanceof Error ? error.message : String(error) }
          : cs
      ),
    }));
  }
}
