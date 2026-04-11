/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useEffect, useRef, useState } from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { fetchFlavor, normalizeBaseUrl, type FlavorInfo } from './lib';
import styles from './styles.module.css';

interface Props {
  language: string;
  code: string;
}

interface CaseResult {
  label: string;
  status: 'passed' | 'failed';
  message?: string;
}

interface VerifyOutcome {
  cases: CaseResult[];
  allPassed: boolean;
}

type Phase = 'idle' | 'connecting' | 'verifying' | 'done' | 'error' | 'cancelled';

interface ResultState {
  phase: Phase;
  /** High-level status (e.g. "Discovering verification cases…", "Verifying X (1/2)…"). */
  message?: string;
  /** Latest progress text from the LSP via `$/progress` notifications. */
  progress?: string;
  outcome?: VerifyOutcome;
}

// LSP / JSON-RPC types — kept hand-rolled to avoid pulling in vscode-languageclient on
// the lightweight verify-only path. Mirrors what the live server bridge speaks.
interface JsonRpcRequest {
  jsonrpc: '2.0';
  id?: number | string;
  method?: string;
  params?: unknown;
  result?: unknown;
  error?: { code: number; message: string };
}

interface VerificationCaseSpec {
  id: string;
  label: string;
  location: {
    uri: string;
    range: {
      start: { line: number; character: number };
      end: { line: number; character: number };
    };
  };
}

interface VerificationCaseRunResult {
  status: 'passed' | 'failed';
  message?: string;
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

const OVERALL_TIMEOUT_MS = 5 * 60 * 1000;

/**
 * Handle to a running verification — exposes a `cancel()` method that the React
 * component wires to its Cancel button.
 */
interface VerifyHandle {
  promise: Promise<VerifyOutcome>;
  cancel: () => void;
}

/**
 * Open a single WebSocket to the live backend, replay the LSP handshake, run
 * `oxsts.case.discover` followed by one `oxsts.case.verify` per case, and resolve
 * with an aggregated outcome.
 *
 * The returned [VerifyHandle.cancel] sends `$/cancelRequest` for any in-flight
 * request and `window/workDoneProgress/cancel` for any open progress token, then
 * closes the socket. The Promise rejects with `'cancelled'` so the caller can
 * distinguish a user-initiated abort from a real failure.
 *
 * Server-initiated requests (specifically `window/workDoneProgress/create`) **must**
 * be answered or the LSP deadlocks at `WorkManager.beginWork(...).join()`. Progress
 * notifications (`$/progress`) double as a UI signal and as keep-alive traffic so
 * Jetty's WebSocket idle timeout doesn't close the connection mid-verification.
 */
function startVerification(
  liveBackendUrl: string,
  flavor: FlavorInfo,
  language: string,
  code: string,
  onPhase: (phase: Phase, message?: string) => void,
  onProgress: (text: string) => void,
): VerifyHandle {
  if (!flavor.verifyCommand) {
    return {
      promise: Promise.reject(
        new Error(`Verification is not enabled for the "${flavor.id}" flavor`),
      ),
      cancel: () => {},
    };
  }

  let cancelFn = () => {};
  const promise = new Promise<VerifyOutcome>((resolve, reject) => {
    const { ws: wsBase } = normalizeBaseUrl(liveBackendUrl);
    const wsUrl = `${wsBase}/ws/lsp/${encodeURIComponent(language)}`;
    const ws = new WebSocket(wsUrl);

    let nextId = 1;
    const pending = new Map<
      number,
      { resolve: (v: unknown) => void; reject: (e: Error) => void; method: string }
    >();
    const activeProgressTokens = new Set<string | number>();
    let cancelled = false;

    const overallTimer = setTimeout(() => {
      cleanup();
      reject(new Error('Verification timed out'));
    }, OVERALL_TIMEOUT_MS);

    let cleaned = false;
    const cleanup = () => {
      if (cleaned) return;
      cleaned = true;
      clearTimeout(overallTimer);
      try {
        ws.close();
      } catch {
        /* ignore */
      }
    };

    const sendRaw = (msg: JsonRpcRequest) => {
      if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg));
    };

    const sendRequest = <T,>(method: string, params: unknown): Promise<T> =>
      new Promise<T>((res, rej) => {
        const id = nextId++;
        pending.set(id, {
          method,
          resolve: (v) => res(v as T),
          reject: rej,
        });
        sendRaw({ jsonrpc: '2.0', id, method, params });
      });

    const sendNotification = (method: string, params: unknown) => {
      sendRaw({ jsonrpc: '2.0', method, params });
    };

    const respondToServerRequest = (id: number | string, result: unknown) => {
      sendRaw({ jsonrpc: '2.0', id, result } as JsonRpcRequest);
    };

    cancelFn = () => {
      if (cancelled || cleaned) return;
      cancelled = true;
      // Cancel every in-flight request the server might still be working on, and
      // signal cancellation on every open progress token. We can't know exactly which
      // request id maps to which token, so we hit them all — the server only acts on
      // the ones it actually owns.
      for (const id of pending.keys()) {
        sendNotification('$/cancelRequest', { id });
      }
      for (const token of activeProgressTokens) {
        sendNotification('window/workDoneProgress/cancel', { token });
      }
      // Reject any in-flight handlers with a sentinel so the outer await unwinds
      // cleanly.
      for (const handler of pending.values()) {
        handler.reject(new Error('cancelled'));
      }
      pending.clear();
      cleanup();
      reject(new Error('cancelled'));
    };

    const handleServerRequest = (msg: JsonRpcRequest) => {
      if (msg.id === undefined || msg.method === undefined) return;
      // The only server→client request the LSP4J language server actually waits on
      // is `window/workDoneProgress/create`. Acking unblocks WorkManager.beginWork.
      if (msg.method === 'window/workDoneProgress/create') {
        const params = msg.params as { token?: string | number } | undefined;
        if (params?.token !== undefined) activeProgressTokens.add(params.token);
        respondToServerRequest(msg.id, null);
        return;
      }
      // Anything else gets a generic null result so the LSP doesn't hang on a
      // capability we don't care about.
      respondToServerRequest(msg.id, null);
    };

    const handleProgressNotification = (params: ProgressParams) => {
      const { value, token } = params;
      if (value.kind === 'begin' && value.title) {
        onProgress(value.title + (value.message ? `: ${value.message}` : ''));
      } else if (value.kind === 'report' && value.message) {
        onProgress(value.message);
      } else if (value.kind === 'end') {
        activeProgressTokens.delete(token);
      }
    };

    ws.onmessage = (event) => {
      let msg: JsonRpcRequest;
      try {
        msg = JSON.parse(event.data as string);
      } catch {
        return;
      }
      // Server-initiated request: respond so the LSP doesn't deadlock.
      if (msg.id !== undefined && msg.method !== undefined) {
        handleServerRequest(msg);
        return;
      }
      // Server-initiated notification.
      if (msg.id === undefined && msg.method !== undefined) {
        if (msg.method === '$/progress') {
          handleProgressNotification(msg.params as ProgressParams);
        }
        return;
      }
      // Response to a request we sent.
      if (msg.id !== undefined) {
        const handler = pending.get(msg.id as number);
        if (handler) {
          pending.delete(msg.id as number);
          if (msg.error) {
            handler.reject(new Error(msg.error.message));
          } else {
            handler.resolve(msg.result);
          }
        }
      }
    };

    ws.onerror = () => {
      if (!cancelled) {
        cleanup();
        reject(new Error('Could not connect to the live server'));
      }
    };

    ws.onclose = () => {
      if (pending.size > 0) {
        for (const { reject: r } of pending.values()) {
          r(new Error('Connection to live server closed'));
        }
        pending.clear();
      }
      clearTimeout(overallTimer);
    };

    ws.onopen = () => {
      void (async () => {
        try {
          onPhase('connecting', 'Initialising language server…');
          const fileUri = `file:///workspace/${flavor.fileName}`;

          await sendRequest('initialize', {
            processId: null,
            clientInfo: { name: 'semantifyr-verify-button' },
            rootUri: 'file:///workspace/',
            workspaceFolders: [{ uri: 'file:///workspace/', name: 'workspace' }],
            capabilities: {
              textDocument: {
                synchronization: { didSave: false, willSave: false, willSaveWaitUntil: false },
              },
              workspace: { workspaceFolders: true, executeCommand: {} },
              window: { workDoneProgress: true },
            },
          });

          sendNotification('initialized', {});
          sendNotification('textDocument/didOpen', {
            textDocument: { uri: fileUri, languageId: language, version: 1, text: code },
          });

          onPhase('verifying', 'Discovering verification cases…');
          const cases = (await sendRequest<VerificationCaseSpec[]>(
            'workspace/executeCommand',
            { command: 'oxsts.case.discover', arguments: [fileUri] },
          )) ?? [];

          if (cases.length === 0) {
            throw new Error('No verification cases found in this snippet');
          }

          const results: CaseResult[] = [];
          for (let i = 0; i < cases.length; i++) {
            if (cancelled) throw new Error('cancelled');
            const c = cases[i];
            onPhase('verifying', `Verifying ${c.label} (${i + 1}/${cases.length})…`);
            const r = await sendRequest<VerificationCaseRunResult>('workspace/executeCommand', {
              command: flavor.verifyCommand!,
              arguments: [{ uri: fileUri, range: c.location.range }],
            });
            results.push({ label: c.label, status: r.status, message: r.message });
          }

          // Best-effort graceful shutdown — don't fail the result if it doesn't complete.
          try {
            await sendRequest('shutdown', null);
            sendNotification('exit', null);
          } catch {
            /* ignore */
          }

          cleanup();
          resolve({
            cases: results,
            allPassed: results.every((r) => r.status === 'passed'),
          });
        } catch (e) {
          cleanup();
          reject(e instanceof Error ? e : new Error(String(e)));
        }
      })();
    };
  });

  return { promise, cancel: cancelFn };
}

/**
 * Lightweight "Verify this snippet" button — opens a transient WebSocket to the live
 * backend, runs the LSP discover/verify dance, and renders pass/fail inline. Does
 * not mount Monaco or the language client; the chunk this lazy-loads is a few KB.
 *
 * While verification is running the button toggles to a Cancel button (with a
 * spinner) and the latest LSP `$/progress` message is shown underneath.
 */
export default function VerifyButton({ language, code }: Props): React.JSX.Element | null {
  const { siteConfig } = useDocusaurusContext();
  const liveBackendUrl = (siteConfig.customFields?.liveBackendUrl as string | undefined) ?? '';

  const [flavor, setFlavor] = useState<FlavorInfo | null>(null);
  const [backendAvailable, setBackendAvailable] = useState<boolean | null>(null);
  const [state, setState] = useState<ResultState>({ phase: 'idle' });
  const handleRef = useRef<VerifyHandle | null>(null);

  useEffect(() => {
    if (!liveBackendUrl) return;
    let cancelled = false;
    const { http } = normalizeBaseUrl(liveBackendUrl);
    fetchFlavor(http, language)
      .then((f) => {
        if (!cancelled) {
          setFlavor(f);
          setBackendAvailable(true);
        }
      })
      .catch(() => {
        if (!cancelled) setBackendAvailable(false);
      });
    return () => {
      cancelled = true;
    };
  }, [liveBackendUrl, language]);

  // Don't render anything if there's no backend URL configured at build time.
  if (!liveBackendUrl) return null;
  // Don't render until we know whether the backend is up.
  if (backendAvailable === null) return null;
  // If the backend is down, show a subtle indicator instead of silently hiding.
  if (!backendAvailable || !flavor || !flavor.verify || !flavor.verifyCommand) {
    return backendAvailable === false ? (
      <div className={styles.verifyContainer}>
        <span className={styles.verifyOffline}>Verification server is currently unavailable</span>
      </div>
    ) : null;
  }

  const startVerify = () => {
    setState({ phase: 'connecting', message: 'Connecting to live server…' });
    const handle = startVerification(
      liveBackendUrl,
      flavor,
      language,
      code,
      (phase, message) => setState((prev) => ({ ...prev, phase, message })),
      (progress) => setState((prev) => ({ ...prev, progress })),
    );
    handleRef.current = handle;
    handle.promise
      .then((outcome) => {
        handleRef.current = null;
        setState({ phase: 'done', outcome });
      })
      .catch((e: Error) => {
        handleRef.current = null;
        if (e.message === 'cancelled') {
          setState({ phase: 'cancelled', message: 'Verification cancelled' });
        } else {
          setState({ phase: 'error', message: e.message });
        }
      });
  };

  const cancelVerify = () => {
    handleRef.current?.cancel();
  };

  const busy = state.phase === 'connecting' || state.phase === 'verifying';

  return (
    <div className={styles.verifyContainer}>
      {busy ? (
        <div className={styles.verifyBusyRow}>
          <span className={styles.verifySpinner} aria-hidden="true" />
          <span className={styles.verifyBusyLabel}>{state.message ?? 'Verifying…'}</span>
          <button
            type="button"
            className={`${styles.toolbarButton} ${styles.verifyButton}`}
            onClick={cancelVerify}
          >
            Cancel
          </button>
        </div>
      ) : (
        <button
          type="button"
          className={`${styles.toolbarButton} ${styles.toolbarButtonPrimary} ${styles.verifyButton}`}
          onClick={startVerify}
        >
          Verify this snippet
        </button>
      )}

      {busy && state.progress && (
        <div className={styles.verifyProgress}>{state.progress}</div>
      )}

      {state.phase === 'done' && state.outcome && (
        <div
          className={
            state.outcome.allPassed
              ? `${styles.verifyResult} ${styles.verifyResultPassed}`
              : `${styles.verifyResult} ${styles.verifyResultFailed}`
          }
        >
          {state.outcome.allPassed
            ? `✓ All ${state.outcome.cases.length} verification case${state.outcome.cases.length === 1 ? '' : 's'} passed`
            : `✗ ${state.outcome.cases.filter((c) => c.status !== 'passed').length} of ${state.outcome.cases.length} cases failed`}
          {!state.outcome.allPassed && (
            <ul className={styles.verifyCaseList}>
              {state.outcome.cases.map((c) => (
                <li key={c.label}>
                  {c.status === 'passed' ? '✓' : '✗'} {c.label}
                  {c.message && c.status !== 'passed' && <span>: {c.message}</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {state.phase === 'error' && (
        <div className={`${styles.verifyResult} ${styles.verifyResultFailed}`}>
          ✗ {state.message}
        </div>
      )}
      {state.phase === 'cancelled' && (
        <div className={styles.verifyResult}>{state.message}</div>
      )}
    </div>
  );
}
