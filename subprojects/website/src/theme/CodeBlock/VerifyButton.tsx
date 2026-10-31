/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useEffect, useRef, useState } from 'react';
import {
  discoverVerificationCases,
  verifyCase,
  type FlavorInfo,
  type LspClient,
  type VerificationStatus,
} from '@semantifyr/editor-common';
import { fetchLiveSetup, normalizeBaseUrl, useLiveBackendUrl, type LiveSetup } from '@site/src/lib/live';
import styles from './styles.module.css';

interface Props {
  language: string;
  code: string;
}

interface CaseOutcome {
  label: string;
  status: VerificationStatus;
  message: string | null;
}

type State =
  | { phase: 'idle' }
  | { phase: 'running'; step: string; progress: string | null }
  | { phase: 'done'; cases: CaseOutcome[] }
  | { phase: 'failed'; message: string };

interface JsonRpcMessage {
  id?: number | string;
  method?: string;
  params?: unknown;
  result?: unknown;
  error?: { message: string };
}

interface ProgressParams {
  token: string | number;
  value: { kind: 'begin' | 'report' | 'end'; title?: string; message?: string };
}

const TIMEOUT_MS = 5 * 60 * 1000;

class LiveConnection implements LspClient {
  private readonly socket: WebSocket;
  private readonly onProgress: (text: string) => void;
  private readonly pending = new Map<number, { resolve: (value: unknown) => void; reject: (error: Error) => void }>();
  private readonly progressTokens = new Set<string | number>();
  private nextId = 1;
  private failure: Error | null = null;

  private constructor(socket: WebSocket, onProgress: (text: string) => void) {
    this.socket = socket;
    this.onProgress = onProgress;
    socket.onmessage = (event) => {
      this.receive(JSON.parse(String(event.data)) as JsonRpcMessage);
    };
    socket.onclose = (event) => {
      this.fail(new Error(event.reason || 'The live server closed the connection'));
    };
  }

  static open(url: string, onProgress: (text: string) => void): Promise<LiveConnection> {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(url);
      socket.onopen = () => {
        resolve(new LiveConnection(socket, onProgress));
      };
      socket.onerror = () => {
        reject(new Error('Could not connect to the live server'));
      };
    });
  }

  sendRequest(method: string, params?: unknown): Promise<unknown> {
    if (this.failure) {
      return Promise.reject(this.failure);
    }
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.send({ id, method, params });
    });
  }

  sendNotification(method: string, params?: unknown): void {
    this.send({ method, params });
  }

  abort(reason: Error): void {
    for (const id of this.pending.keys()) {
      this.sendNotification('$/cancelRequest', { id });
    }
    for (const token of this.progressTokens) {
      this.sendNotification('window/workDoneProgress/cancel', { token });
    }
    this.fail(reason);
    this.socket.close();
  }

  close(): void {
    this.socket.close();
  }

  private send(message: JsonRpcMessage): void {
    if (this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ jsonrpc: '2.0', ...message }));
    }
  }

  private fail(reason: Error): void {
    this.failure ??= reason;
    for (const { reject } of this.pending.values()) {
      reject(this.failure);
    }
    this.pending.clear();
  }

  private receive(message: JsonRpcMessage): void {
    if (message.method !== undefined) {
      if (message.id !== undefined) {
        // lsp4j blocks the verification until window/workDoneProgress/create is answered
        if (message.method === 'window/workDoneProgress/create') {
          this.progressTokens.add((message.params as { token: string | number }).token);
        }
        this.send({ id: message.id, result: null });
      } else if (message.method === '$/progress') {
        this.receiveProgress(message.params as ProgressParams);
      }
      return;
    }
    const handler = typeof message.id === 'number' ? this.pending.get(message.id) : undefined;
    if (handler === undefined) {
      return;
    }
    this.pending.delete(message.id as number);
    if (message.error) {
      handler.reject(new Error(message.error.message));
    } else {
      handler.resolve(message.result);
    }
  }

  private receiveProgress({ token, value }: ProgressParams): void {
    if (value.kind === 'end') {
      this.progressTokens.delete(token);
      return;
    }
    const text = [value.title, value.message].filter((part) => part !== undefined).join(': ');
    if (text) {
      this.onProgress(text);
    }
  }
}

async function verifySnippet(
  connection: LiveConnection,
  flavor: FlavorInfo,
  portfolio: string,
  code: string,
  onStep: (step: string) => void,
): Promise<CaseOutcome[]> {
  const uri = `file:///workspace/${flavor.fileName}`;

  onStep('Starting the language server');
  await connection.sendRequest('initialize', {
    processId: null,
    rootUri: 'file:///workspace/',
    capabilities: { window: { workDoneProgress: true } },
  });
  connection.sendNotification('initialized', {});
  connection.sendNotification('textDocument/didOpen', {
    textDocument: { uri, languageId: flavor.languageId, version: 1, text: code },
  });

  onStep('Discovering verification cases');
  const cases = await discoverVerificationCases(connection, flavor.discoveryCommand, uri);
  if (cases.length === 0) {
    throw new Error('No verification cases found in this snippet');
  }

  const outcomes: CaseOutcome[] = [];
  for (const [index, verificationCase] of cases.entries()) {
    onStep(`Verifying ${verificationCase.label} (${index + 1}/${cases.length})`);
    const result = await verifyCase(
      connection,
      flavor.verificationCommand,
      { uri, range: verificationCase.location.range },
      { portfolio },
    );
    outcomes.push({
      label: verificationCase.label,
      status: result?.status ?? 'errored',
      message: result?.message ?? null,
    });
  }
  return outcomes;
}

export default function VerifyButton({ language, code }: Props): React.JSX.Element | null {
  const liveBackendUrl = useLiveBackendUrl();
  const [setup, setSetup] = useState<LiveSetup | 'checking' | 'unavailable'>('checking');
  const [state, setState] = useState<State>({ phase: 'idle' });
  const connectionRef = useRef<LiveConnection | null>(null);

  useEffect(() => {
    let active = true;
    fetchLiveSetup(liveBackendUrl).then(
      (fetched) => {
        if (active) {
          setSetup(fetched);
        }
      },
      () => {
        if (active) {
          setSetup('unavailable');
        }
      },
    );
    return () => {
      active = false;
    };
  }, [liveBackendUrl]);

  useEffect(() => {
    return () => {
      connectionRef.current?.close();
    };
  }, []);

  if (setup === 'checking') {
    return null;
  }
  if (setup === 'unavailable') {
    return (
      <div className={styles.verifyContainer}>
        <span className={styles.verifyOffline}>Verification server is currently unavailable</span>
      </div>
    );
  }
  const flavor = setup.flavors.find((candidate) => candidate.id === language);
  if (flavor === undefined) {
    return null;
  }
  const { defaultPortfolio } = setup;

  const start = async (): Promise<void> => {
    setState({ phase: 'running', step: 'Connecting to the live server', progress: null });
    let connection: LiveConnection | null = null;
    const timer = setTimeout(() => {
      connection?.abort(new Error('Verification timed out'));
    }, TIMEOUT_MS);
    try {
      const url = `${normalizeBaseUrl(liveBackendUrl).ws}/ws/lsp/${encodeURIComponent(flavor.id)}`;
      connection = await LiveConnection.open(url, (progress) => {
        setState((previous) => (previous.phase === 'running' ? { ...previous, progress } : previous));
      });
      connectionRef.current = connection;
      const cases = await verifySnippet(connection, flavor, defaultPortfolio, code, (step) => {
        setState({ phase: 'running', step, progress: null });
      });
      setState({ phase: 'done', cases });
    } catch (error) {
      setState({ phase: 'failed', message: error instanceof Error ? error.message : String(error) });
    } finally {
      clearTimeout(timer);
      connection?.close();
      connectionRef.current = null;
    }
  };

  const cancel = (): void => {
    connectionRef.current?.abort(new Error('Verification cancelled'));
  };

  if (state.phase === 'running') {
    return (
      <div className={styles.verifyContainer}>
        <div className={styles.verifyBusyRow}>
          <span className={styles.verifySpinner} aria-hidden="true" />
          <span className={styles.verifyBusyLabel}>{state.step}</span>
          <button type="button" className={styles.toolbarButton} onClick={cancel}>
            Cancel
          </button>
        </div>
        {state.progress && <div className={styles.verifyProgress}>{state.progress}</div>}
      </div>
    );
  }

  return (
    <div className={styles.verifyContainer}>
      <button
        type="button"
        className={`${styles.toolbarButton} ${styles.toolbarButtonPrimary}`}
        onClick={() => {
          void start();
        }}
      >
        Verify this snippet
      </button>
      {state.phase === 'done' && (
        <ul className={styles.verifyResult}>
          {state.cases.map((outcome) => (
            <li
              key={outcome.label}
              className={outcome.status === 'passed' ? styles.verifyResultPassed : styles.verifyResultFailed}
            >
              {outcome.label}: {outcome.status.replace('_', ' ')}
              {outcome.message && outcome.status !== 'passed' && ` (${outcome.message})`}
            </li>
          ))}
        </ul>
      )}
      {state.phase === 'failed' && (
        <div className={`${styles.verifyResult} ${styles.verifyResultFailed}`}>{state.message}</div>
      )}
    </div>
  );
}
