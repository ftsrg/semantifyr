/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import {
  LIVE_FLAVORS,
  findExample,
  findFlavor,
  type LiveExample,
  type LiveFlavor,
} from '../examples';
import { decodeBase64Url, encodeBase64Url } from '../lib/urls';
import type { ColorModePreference, ResolvedColorMode } from '../lib/theme';
import type { FlavorInfo } from '../lib/flavors';
import type {
  LiveEditorHandle,
  LiveEditorStatus,
} from './LiveEditor';
import Toolbar from './Toolbar';
import StatusBar, { type StatusBarInfoItem } from './StatusBar';
import DevInfoPanel from './DevInfoPanel';
import OxstsPreviewPane from './OxstsPreviewPane';
import { VerificationPanel } from './verification';

const LiveEditor = lazy(() => import('./LiveEditor'));

const DEFAULT_FLAVOR = LIVE_FLAVORS[0]!;

function resolveInitialFlavor(search: string): LiveFlavor {
  const params = new URLSearchParams(search);
  const modeParam = params.get('mode');
  return (modeParam ? findFlavor(modeParam) : undefined) ?? DEFAULT_FLAVOR;
}

interface Props {
  backendUrl: string;
  colorMode: ResolvedColorMode;
  colorModePreference: ColorModePreference;
  onToggleColorMode: () => void;
}

interface UrlState {
  flavor: LiveFlavor;
  example: LiveExample;
  code: string;
}

function resolveInitialState(search: string): UrlState {
  const flavor = resolveInitialFlavor(search);
  const params = new URLSearchParams(search);
  const exampleParam = params.get('example');
  const codeParam = params.get('code');

  if (exampleParam) {
    const example = findExample(flavor, exampleParam) ?? flavor.examples[0]!;
    const code = codeParam ? decodeBase64Url(codeParam) ?? example.code : example.code;
    return { flavor, example, code };
  }

  if (codeParam) {
    const decoded = decodeBase64Url(codeParam);
    const example = flavor.examples[0]!;
    return { flavor, example, code: decoded ?? example.code };
  }

  const example = flavor.examples[0]!;
  return { flavor, example, code: example.code };
}

function deriveConnectionMessage(status: LiveEditorStatus, statusInfo: string | null): string {
  switch (status) {
    case 'initializing': return statusInfo ?? 'Initializing...';
    case 'reconnecting': return statusInfo ?? 'Reconnecting...';
    case 'errored': return statusInfo ?? 'Connection error';
    case 'disconnected': return 'Disconnected';
    case 'connected': return 'Ready';
  }
}

export default function EditorPage({
  backendUrl,
  colorMode,
  colorModePreference,
  onToggleColorMode,
}: Props): React.JSX.Element {
  const [flavor, setFlavor] = useState<LiveFlavor>(DEFAULT_FLAVOR);
  const [example, setExample] = useState<LiveExample>(DEFAULT_FLAVOR.examples[0]!);
  const [code, setCode] = useState<string>(DEFAULT_FLAVOR.examples[0]!.code);
  const [copyConfirmation, setCopyConfirmation] = useState<string | null>(null);

  const [status, setStatus] = useState<LiveEditorStatus>('initializing');
  const [statusInfo, setStatusInfo] = useState<string | null>(null);
  const [flavorInfo, setFlavorInfo] = useState<FlavorInfo | null>(null);
  const [verificationStatusMessage, setVerificationStatusMessage] = useState<string | null>(null);
  const [devPanelOpen, setDevPanelOpen] = useState(false);
  const [connectedSince, setConnectedSince] = useState<number | null>(null);
  const [reconnectCount, setReconnectCount] = useState(0);
  const [generatedOxsts, setGeneratedOxsts] = useState<string | null>(null);
  const [generatedOxstsAt, setGeneratedOxstsAt] = useState<number | null>(null);

  const editorHandleRef = useRef<LiveEditorHandle | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const initial = resolveInitialState(window.location.search);
    setFlavor(initial.flavor);
    setExample(initial.example);
    setCode(initial.code);
  }, []);

  const handleSelectFlavor = useCallback((flavorId: string) => {
    const next = findFlavor(flavorId);
    if (!next || next.id === flavor.id) return;
    const firstExample = next.examples[0]!;
    setFlavor(next);
    setExample(firstExample);
    setCode(firstExample.code);
    setGeneratedOxsts(null);
    setGeneratedOxstsAt(null);
    if (typeof window !== 'undefined') {
      const url = new URL(window.location.href);
      url.searchParams.set('mode', next.id);
      url.searchParams.delete('example');
      url.searchParams.delete('code');
      window.history.replaceState(null, '', url.toString());
    }
  }, [flavor.id]);

  const handleLoadExample = useCallback((exampleId: string) => {
    const next = findExample(flavor, exampleId);
    if (!next) return;
    setExample(next);
    setCode(next.code);
  }, [flavor]);

  const handleCopyLink = useCallback(async () => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams();
    params.set('mode', flavor.id);
    params.set('example', example.id);
    params.set('code', encodeBase64Url(code));
    const url = `${window.location.origin}${window.location.pathname}?${params.toString()}`;
    try {
      await navigator.clipboard.writeText(url);
      setCopyConfirmation('Link copied!');
    } catch {
      setCopyConfirmation('Copy failed');
    }
    setTimeout(() => setCopyConfirmation(null), 2000);
  }, [flavor.id, example.id, code]);

  const editorKey = useMemo(() => `${flavor.id}:${example.id}`, [flavor.id, example.id]);

  const showVerificationPanel =
    !!flavorInfo?.verify && !!flavorInfo.verificationCommand && !!flavorInfo.discoveryCommand;
  const showPeekPane = flavor.peekCompiledOutput;
  const logoSrc = colorMode === 'dark' ? '/logo-full-dark.svg' : '/logo-full-light.svg';

  const statusBarMessage = verificationStatusMessage ?? deriveConnectionMessage(status, statusInfo);
  const statusBarShowProgress =
    verificationStatusMessage !== null ||
    status === 'initializing' ||
    status === 'reconnecting';

  const handleVerificationStatus = useCallback((message: string | null) => {
    setVerificationStatusMessage(message);
  }, []);

  const statusBarInfoItems = useMemo((): StatusBarInfoItem[] => [
    { label: 'Mode', value: flavor.displayName, tooltip: `Mode: ${flavor.displayName}` },
  ], [flavor.displayName]);

  useEffect(() => {
    if (!showPeekPane) return;
    const handle = editorHandleRef.current;
    if (!handle) return;
    const dispose = handle.addNotificationListener('semantifyr/gamma/oxstsGenerated', (params: unknown) => {
      const p = params as { oxstsSource?: string } | undefined;
      if (typeof p?.oxstsSource !== 'string') return;
      setGeneratedOxsts(p.oxstsSource);
      setGeneratedOxstsAt(Date.now());
    });
    return () => dispose?.();
    // Re-register when flavor switches or the editor handle changes after remount.
  }, [showPeekPane, editorKey]);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', minHeight: 480, bgcolor: 'var(--page-bg)' }}>
      <Toolbar
        logoSrc={logoSrc}
        flavors={LIVE_FLAVORS}
        currentFlavorId={flavor.id}
        onSelectFlavor={handleSelectFlavor}
        examples={flavor.examples}
        onLoadExample={handleLoadExample}
        connectionStatus={status}
        onReconnect={() => editorHandleRef.current?.reconnect()}
        onDisconnect={() => editorHandleRef.current?.disconnect()}
        onCopyLink={handleCopyLink}
        copyConfirmation={copyConfirmation}
        colorModePreference={colorModePreference}
        onToggleColorMode={onToggleColorMode}
      />

      <Box sx={{ flex: '1 1 auto', minHeight: 0, display: 'flex', flexDirection: 'row' }}>
        <Box sx={{ flex: '1 1 0', minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          <Suspense
            fallback={
              <Box sx={{ flex: '1 1 auto', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2, bgcolor: 'var(--page-bg)' }}>
                <CircularProgress size={32} sx={{ color: 'var(--text-muted)' }} />
                <Typography sx={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                  Loading editor...
                </Typography>
              </Box>
            }
          >
            <LiveEditor
              ref={editorHandleRef}
              key={editorKey}
              flavorId={flavor.id}
              languageId={flavor.languageId}
              fileName={flavor.fileName}
              initialCode={code}
              backendUrl={backendUrl}
              colorMode={colorMode}
              fillParent
              onStatusChange={(next, info) => {
                setStatus((prev) => {
                  if (next === 'connected' && prev !== 'connected') {
                    setConnectedSince(Date.now());
                    if (prev === 'reconnecting') setReconnectCount((c) => c + 1);
                  } else if (next !== 'connected') {
                    setConnectedSince(null);
                  }
                  return next;
                });
                setStatusInfo(info ?? null);
              }}
              onFlavorReady={setFlavorInfo}
            />
          </Suspense>
        </Box>
        {showPeekPane && (
          <OxstsPreviewPane oxstsSource={generatedOxsts} lastUpdated={generatedOxstsAt} />
        )}
      </Box>

      <Box sx={{ flex: '0 0 auto', display: 'flex', flexDirection: 'column' }}>

        {showVerificationPanel && (
          <VerificationPanel
            editorHandle={editorHandleRef.current}
            verificationCommand={flavorInfo!.verificationCommand!}
            discoveryCommand={flavorInfo!.discoveryCommand!}
            connected={status === 'connected'}
            onStatusMessage={handleVerificationStatus}
          />
        )}

        <StatusBar
          message={statusBarMessage}
          showProgress={statusBarShowProgress}
          infoItems={statusBarInfoItems}
          onInfoClick={() => setDevPanelOpen((prev) => !prev)}
        />
        <DevInfoPanel
          open={devPanelOpen}
          onClose={() => setDevPanelOpen(false)}
          connectionStatus={status}
          language={flavor.displayName}
          connectedSince={connectedSince}
          reconnectCount={reconnectCount}
          editorHandle={editorHandleRef.current}
        />
      </Box>
    </Box>
  );
}
