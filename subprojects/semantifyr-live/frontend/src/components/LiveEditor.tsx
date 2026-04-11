/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
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

export type { LspClient } from '../lib/verification';
export type { VerificationCaseLocation } from '../lib/verification';
export type { LspMetrics } from '../lib/lspMetrics';

export type LiveEditorStatus = 'initializing' | 'connected' | 'disconnected' | 'reconnecting' | 'errored';

export interface LiveEditorHandle {
  reconnect: () => void;
  disconnect: () => void;
  goToCase: (location: VerificationCaseLocation) => void;
  getLspClient: () => LspClient | undefined;
  getLspMetrics: () => LspMetrics | null;
  getFileUri: () => string;
  onEditorContentChange: (callback: () => void) => (() => void) | undefined;
  addProgressListener: (listener: (params: unknown) => void) => () => void;
}

interface Props {
  language: string;
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
    language,
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

  const fileUri = useMemo(() => createFileUri(language), [language]);

  const backendUrlRef = useRef(backendUrl);
  const languageRef = useRef(language);
  useEffect(() => {
    backendUrlRef.current = backendUrl;
    languageRef.current = language;
  });

  useEffect(() => {
    void applyColorTheme(colorMode);
  }, [colorMode]);

  const clearReconnectTimer = (): void => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  };

  const doConnect = async (): Promise<void> => {
    if (languageClientRef.current) {
      try { await languageClientRef.current.dispose(); } catch { /* ignore */ }
      languageClientRef.current = null;
    }
    const connectedClient = await connectLanguageClient(
      backendUrlRef.current,
      languageRef.current,
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
          instance = await createEditor(hostRef.current, colorMode, fileUri, language, initialCode);
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
    fetchFlavor(http, language)
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
  }, [language, backendUrl, initialCode, fileUri]);

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
          return undefined;
        }
        if (!metricsClientRef.current) {
          metricsClientRef.current = wrapClientWithMetrics(rawClient);
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
