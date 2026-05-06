/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import * as vscode from 'vscode';
import type { LanguageClientWrapper } from 'monaco-languageclient/lcwrapper';
import type { EditorApp } from 'monaco-languageclient/editorApp';

import { normalizeBaseUrl } from '../lib/urls';
import { fetchFlavor, type FlavorInfo } from '../lib/flavors';
import type { ResolvedColorMode as ColorMode } from '../lib/theme';
import {
  applyColorTheme,
  connectLanguageClient,
  createEditor,
  createFileUri,
  type EditorInstance,
} from '../lib/monaco';
import type { LspClient, VerificationCaseLocation } from '../lib/verification';
import { wrapClientWithMetrics, type LspMetrics, type MetricsTracker } from '../lib/lspMetrics';

import styles from './LiveEditor.module.css';

export type LiveEditorStatus = 'initializing' | 'connected' | 'disconnected' | 'reconnecting' | 'errored';

export interface ProblemEntry {
  severity: 'error' | 'warning' | 'info' | 'hint';
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
  message: string;
  source: string;
}

export interface LiveEditorHandle {
  reconnect: () => void;
  disconnect: () => void;
  goToCase: (location: VerificationCaseLocation) => void;
  getLspClient: () => LspClient | undefined;
  getLspMetrics: () => LspMetrics | null;
  getFileUri: () => string;
  onEditorContentChange: (callback: () => void) => (() => void) | undefined;
  addProgressListener: (listener: (params: unknown) => void) => () => void;
  addNotificationListener: (method: string, listener: (params: unknown) => void) => () => void;
  /** Set verify-derived markers on the active model. Pass an empty array to clear. */
  setVerifyCaseMarkers: (markers: readonly ProblemEntry[]) => void;
  /** Snapshot of every model marker currently visible (LSP + verify + future sources). */
  getProblems: () => ProblemEntry[];
  /** Subscribe to marker updates. Returns disposer. */
  addProblemsListener: (listener: () => void) => () => void;
  /** Reveal + position the editor at a marker's range and focus it. */
  revealProblem: (problem: ProblemEntry) => void;
}

interface Props {
  flavorId: string;
  languageId: string;
  fileName: string;
  initialCode: string;
  backendUrl: string;
  colorMode: ColorMode;
  fillParent?: boolean;
  onStatusChange?: (status: LiveEditorStatus, info?: string) => void;
  onFlavorReady?: (flavor: FlavorInfo | null) => void;
}

const MAX_RECONNECT_ATTEMPTS = 3;
const RECONNECT_BACKOFF_MS = [1000, 2000, 4000];

const LiveEditor = forwardRef<LiveEditorHandle, Props>(function LiveEditor(
  {
    flavorId,
    languageId,
    fileName,
    initialCode,
    backendUrl,
    colorMode,
    fillParent = false,
    onStatusChange,
    onFlavorReady,
  },
  ref,
): React.JSX.Element {
  const hostRef = useRef<HTMLDivElement>(null);
  const editorAppRef = useRef<EditorApp | null>(null);
  const languageClientRef = useRef<LanguageClientWrapper | null>(null);
  const fsOverlayRef = useRef<{ dispose: () => void } | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cancelledRef = useRef(false);
  const flavorRef = useRef<FlavorInfo | null>(null);
  const progressListenersRef = useRef<Set<(params: unknown) => void>>(new Set());
  const metricsClientRef = useRef<(LspClient & MetricsTracker) | null>(null);
  const metricsRawClientRef = useRef<LspClient | null>(null);
  // Verify-derived diagnostics live in their own collection so we can replace them wholesale
  // without touching LSP-emitted diagnostics under the language-server's owner.
  const verifyDiagnosticsRef = useRef<vscode.DiagnosticCollection | null>(null);
  const problemsListenersRef = useRef<Set<() => void>>(new Set());

  const [, setInternalStatus] = useState<LiveEditorStatus>('initializing');

  const onStatusChangeRef = useRef(onStatusChange);
  const onFlavorReadyRef = useRef(onFlavorReady);
  useEffect(() => {
    onStatusChangeRef.current = onStatusChange;
    onFlavorReadyRef.current = onFlavorReady;
  });

  const reportStatus = (newStatus: LiveEditorStatus, info?: string): void => {
    setInternalStatus(newStatus);
    onStatusChangeRef.current?.(newStatus, info);
  };

  const fileUri = useMemo(() => createFileUri(fileName), [fileName]);

  const verifyMarkersSubscriptionsRef = useRef<vscode.Disposable[] | null>(null);

  // Lazy bring-up. The vscode services aren't ready until `createEditor` has run inside the
  // editor-bootstrap effect; calling `vscode.languages.*` before that throws "Default api is
  // not ready yet". Every handle method routes through this getter, which sets up the
  // collection + listeners on first use (always after the editor mounted).
  const ensureVerifyDiagnostics = (): vscode.DiagnosticCollection | null => {
    if (verifyDiagnosticsRef.current) return verifyDiagnosticsRef.current;
    try {
      const collection = vscode.languages.createDiagnosticCollection('semantifyr-verify');
      const subs: vscode.Disposable[] = [
        vscode.languages.onDidChangeDiagnostics(() => {
          for (const listener of problemsListenersRef.current) {
            try { listener(); } catch { /* ignore */ }
          }
        }),
        // Edits invalidate the previous verdict, so wipe the verify markers when the user types.
        vscode.workspace.onDidChangeTextDocument(() => {
          verifyDiagnosticsRef.current?.clear();
        }),
      ];
      verifyDiagnosticsRef.current = collection;
      verifyMarkersSubscriptionsRef.current = subs;
      return collection;
    } catch {
      // vscode services still not ready - the caller will retry on the next listener tick.
      return null;
    }
  };

  // Component-unmount cleanup for whatever ensureVerifyDiagnostics installed.
  useEffect(() => {
    return () => {
      verifyMarkersSubscriptionsRef.current?.forEach((d) => { try { d.dispose(); } catch { /* ignore */ } });
      verifyMarkersSubscriptionsRef.current = null;
      try { verifyDiagnosticsRef.current?.dispose(); } catch { /* ignore */ }
      verifyDiagnosticsRef.current = null;
    };
  }, []);

  const backendUrlRef = useRef(backendUrl);
  const flavorIdRef = useRef(flavorId);
  const languageIdRef = useRef(languageId);
  useEffect(() => {
    backendUrlRef.current = backendUrl;
    flavorIdRef.current = flavorId;
    languageIdRef.current = languageId;
  });

  const notificationHandlersRef = useRef<Map<string, Set<(params: unknown) => void>>>(new Map());
  const attachedNotificationDisposablesRef = useRef<Array<{ dispose: () => void }>>([]);

  useEffect(() => {
    void applyColorTheme(colorMode);
  }, [colorMode]);

  const clearReconnectTimer = (): void => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  };

  const attachNotificationHandlers = (client: LanguageClientWrapper): void => {
    for (const d of attachedNotificationDisposablesRef.current) {
      try { d.dispose(); } catch { /* ignore */ }
    }
    attachedNotificationDisposablesRef.current = [];
    const raw = client.getLanguageClient() as unknown as {
      onNotification: (method: string, cb: (params: unknown) => void) => { dispose: () => void };
    } | undefined;
    if (!raw) return;
    for (const [method, listeners] of notificationHandlersRef.current) {
      try {
        const disposable = raw.onNotification(method, (params) => {
          for (const listener of listeners) {
            try { listener(params); }
            catch (error) { console.warn(`semantifyr-live: notification listener for ${method} threw`, error); }
          }
        });
        if (disposable) attachedNotificationDisposablesRef.current.push(disposable);
      } catch (error) {
        console.warn(`semantifyr-live: failed to attach notification listener for ${method}`, error);
      }
    }
  };

  const doConnect = async (): Promise<void> => {
    if (languageClientRef.current) {
      try { await languageClientRef.current.dispose(); } catch { /* ignore */ }
      languageClientRef.current = null;
    }
    const connectedClient = await connectLanguageClient(
      backendUrlRef.current,
      flavorIdRef.current,
      languageIdRef.current,
      {
        onProgress: (params: unknown) => {
          for (const listener of progressListenersRef.current) {
            try { listener(params); }
            catch (error) { console.warn('semantifyr-live: progress listener threw', error); }
          }
        },
        onStopped: () => {
          if (cancelledRef.current) return;
          scheduleReconnect();
        },
      },
    );
    if (cancelledRef.current) {
      try { await connectedClient.dispose(); } catch { /* ignore */ }
      return;
    }
    languageClientRef.current = connectedClient;
    attachNotificationHandlers(connectedClient);
  };

  const scheduleReconnect = (): void => {
    if (cancelledRef.current) return;
    clearReconnectTimer();
    const attempt = reconnectAttemptsRef.current + 1;
    if (attempt > MAX_RECONNECT_ATTEMPTS) {
      reportStatus('errored', 'Connection failed after multiple attempts');
      return;
    }
    reconnectAttemptsRef.current = attempt;
    const backoff = RECONNECT_BACKOFF_MS[attempt - 1] ?? 4000;
    reportStatus('reconnecting', `Reconnecting (attempt ${attempt}/${MAX_RECONNECT_ATTEMPTS})...`);
    reconnectTimerRef.current = setTimeout(async () => {
      reconnectTimerRef.current = null;
      try {
        await doConnect();
        if (cancelledRef.current) return;
        reportStatus('connected');
        reconnectAttemptsRef.current = 0;
      } catch {
        scheduleReconnect();
      }
    }, backoff);
  };

  const handleManualReconnect = (): void => {
    cancelledRef.current = false;
    clearReconnectTimer();
    reconnectAttemptsRef.current = 0;
    reportStatus('reconnecting', 'Reconnecting...');
    doConnect()
      .then(() => {
        if (cancelledRef.current) return;
        reportStatus('connected');
      })
      .catch(() => {
        scheduleReconnect();
      });
  };

  useEffect(() => {
    cancelledRef.current = false;
    let disposed = false;

    const dispose = async (): Promise<void> => {
      if (disposed) return;
      disposed = true;
      clearReconnectTimer();
      try { await languageClientRef.current?.dispose(); } catch { /* ignore */ }
      try { await editorAppRef.current?.dispose(); } catch { /* ignore */ }
      try { fsOverlayRef.current?.dispose(); } catch { /* ignore */ }
      languageClientRef.current = null;
      editorAppRef.current = null;
      fsOverlayRef.current = null;
    };

    (async () => {
      try {
        if (!hostRef.current) return;
        reportStatus('initializing', 'Initializing editor...');

        let instance: EditorInstance;
        try {
          instance = await createEditor(hostRef.current, colorMode, fileUri, languageId, initialCode);
        } catch {
          if (!cancelledRef.current) reportStatus('errored', 'Editor failed to start');
          return;
        }
        if (cancelledRef.current) {
          try { await instance.editorApp.dispose(); } catch { /* ignore */ }
          try { instance.fsOverlay.dispose(); } catch { /* ignore */ }
          return;
        }
        editorAppRef.current = instance.editorApp;
        fsOverlayRef.current = instance.fsOverlay;

        await doConnect();
        if (cancelledRef.current) {
          await dispose();
          return;
        }
        reconnectAttemptsRef.current = 0;
        reportStatus('connected');
      } catch {
        if (!cancelledRef.current) {
          reportStatus('errored', 'Editor failed to start');
        }
      }
    })();

    const { http } = normalizeBaseUrl(backendUrl);
    fetchFlavor(http, flavorId)
      .then((found) => {
        if (cancelledRef.current) return;
        flavorRef.current = found;
        onFlavorReadyRef.current?.(found);
      })
      .catch(() => { /* non-fatal */ });

    return () => {
      cancelledRef.current = true;
      void dispose();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flavorId, languageId, fileName, backendUrl, initialCode, fileUri]);

  useImperativeHandle(
    ref,
    () => ({
      reconnect: handleManualReconnect,
      disconnect: () => {
        if (languageClientRef.current) {
          cancelledRef.current = true;
          clearReconnectTimer();
          void languageClientRef.current.dispose().catch(() => { /* ignore */ });
          languageClientRef.current = null;
          metricsClientRef.current = null;
          metricsRawClientRef.current = null;
          reportStatus('disconnected');
        }
      },
      goToCase: (location) => {
        const editorApp = editorAppRef.current;
        if (!editorApp) return;
        const editor = editorApp.getEditor();
        if (!editor) return;
        const startLine = location.range.start.line + 1;
        const startCol = location.range.start.character + 1;
        editor.revealLineInCenter(startLine);
        editor.setPosition({ lineNumber: startLine, column: startCol });
        editor.focus();
      },
      getLspClient: () => {
        const rawClient = languageClientRef.current?.getLanguageClient() as LspClient | undefined;
        if (!rawClient) {
          metricsClientRef.current = null;
          metricsRawClientRef.current = null;
          return undefined;
        }
        if (!metricsClientRef.current || metricsRawClientRef.current !== rawClient) {
          metricsClientRef.current = wrapClientWithMetrics(rawClient);
          metricsRawClientRef.current = rawClient;
        }
        return metricsClientRef.current;
      },
      getLspMetrics: () => {
        return metricsClientRef.current?.getMetrics() ?? null;
      },
      getFileUri: () => fileUri.toString(),
      onEditorContentChange: (callback: () => void) => {
        const editor = editorAppRef.current?.getEditor();
        const disposable = editor?.onDidChangeModelContent(callback);
        return disposable ? () => disposable.dispose() : undefined;
      },
      addProgressListener: (listener: (params: unknown) => void) => {
        progressListenersRef.current.add(listener);
        return () => { progressListenersRef.current.delete(listener); };
      },
      addNotificationListener: (method: string, listener: (params: unknown) => void) => {
        let listeners = notificationHandlersRef.current.get(method);
        if (!listeners) {
          listeners = new Set();
          notificationHandlersRef.current.set(method, listeners);
          if (languageClientRef.current) attachNotificationHandlers(languageClientRef.current);
        }
        listeners.add(listener);
        return () => {
          const set = notificationHandlersRef.current.get(method);
          if (!set) return;
          set.delete(listener);
          if (set.size === 0) {
            notificationHandlersRef.current.delete(method);
            if (languageClientRef.current) attachNotificationHandlers(languageClientRef.current);
          }
        };
      },
      setVerifyCaseMarkers: (markers) => {
        const collection = ensureVerifyDiagnostics();
        if (!collection) return;
        const diagnostics = markers.map((m) => {
          const range = new vscode.Range(
            Math.max(0, m.startLine),
            Math.max(0, m.startColumn),
            Math.max(0, m.endLine),
            Math.max(0, m.endColumn),
          );
          const diagnostic = new vscode.Diagnostic(range, m.message, severityFromString(m.severity));
          diagnostic.source = m.source;
          return diagnostic;
        });
        collection.set(fileUri, diagnostics);
      },
      getProblems: () => {
        // Touch ensureVerifyDiagnostics so the listener subscription is in place before the
        // caller registers a problems listener; safe to call repeatedly (no-op once init'd).
        ensureVerifyDiagnostics();
        try {
          const all = vscode.languages.getDiagnostics(fileUri);
          return all.map((d) => ({
            severity: severityToString(d.severity),
            startLine: d.range.start.line,
            startColumn: d.range.start.character,
            endLine: d.range.end.line,
            endColumn: d.range.end.character,
            message: d.message,
            source: d.source ?? '',
          }));
        } catch {
          return [];
        }
      },
      addProblemsListener: (listener) => {
        problemsListenersRef.current.add(listener);
        return () => { problemsListenersRef.current.delete(listener); };
      },
      revealProblem: (problem) => {
        const editor = editorAppRef.current?.getEditor();
        if (!editor) return;
        const startLine = problem.startLine + 1;
        const startCol = problem.startColumn + 1;
        editor.revealLineInCenter(startLine);
        editor.setPosition({ lineNumber: startLine, column: startCol });
        editor.focus();
      },
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [fileUri],
  );

  const panelClassName = fillParent
    ? `${styles.editorPanel} ${styles.editorPanelFill}`
    : styles.editorPanel;

  return <div ref={hostRef} className={panelClassName} />;
});

export default LiveEditor;

function severityFromString(s: ProblemEntry['severity']): vscode.DiagnosticSeverity {
  switch (s) {
    case 'error': return vscode.DiagnosticSeverity.Error;
    case 'warning': return vscode.DiagnosticSeverity.Warning;
    case 'info': return vscode.DiagnosticSeverity.Information;
    case 'hint': return vscode.DiagnosticSeverity.Hint;
  }
}

function severityToString(s: vscode.DiagnosticSeverity): ProblemEntry['severity'] {
  switch (s) {
    case vscode.DiagnosticSeverity.Error: return 'error';
    case vscode.DiagnosticSeverity.Warning: return 'warning';
    case vscode.DiagnosticSeverity.Information: return 'info';
    case vscode.DiagnosticSeverity.Hint: return 'hint';
  }
}
