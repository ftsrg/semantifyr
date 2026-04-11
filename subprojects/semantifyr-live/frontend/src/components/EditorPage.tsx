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
  type LiveExample,
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
import { VerificationPanel } from './verification';

const LiveEditor = lazy(() => import('./LiveEditor'));

const DEFAULT_FLAVOR = LIVE_FLAVORS[0]!;

interface Props {
  backendUrl: string;
  colorMode: ResolvedColorMode;
  colorModePreference: ColorModePreference;
  onToggleColorMode: () => void;
}

interface UrlState {
  example: LiveExample;
  code: string;
}

function resolveInitialState(search: string): UrlState {
  const params = new URLSearchParams(search);
  const exampleParam = params.get('example');
  const codeParam = params.get('code');

  if (exampleParam) {
    const example = findExample(DEFAULT_FLAVOR, exampleParam) ?? DEFAULT_FLAVOR.examples[0]!;
    const code = codeParam ? decodeBase64Url(codeParam) ?? example.code : example.code;
    return { example, code };
  }

  if (codeParam) {
    const decoded = decodeBase64Url(codeParam);
    const example = DEFAULT_FLAVOR.examples[0]!;
    return { example, code: decoded ?? example.code };
  }

  const example = DEFAULT_FLAVOR.examples[0]!;
  return { example, code: example.code };
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
  const [example, setExample] = useState<LiveExample>(DEFAULT_FLAVOR.examples[0]!);
  const [code, setCode] = useState<string>(DEFAULT_FLAVOR.examples[0]!.code);
  const [copyConfirmation, setCopyConfirmation] = useState<string | null>(null);

  const [status, setStatus] = useState<LiveEditorStatus>('initializing');
  const [statusInfo, setStatusInfo] = useState<string | null>(null);
  const [flavorInfo, setFlavorInfo] = useState<FlavorInfo | null>(null);
  const [verificationStatusMessage, setVerificationStatusMessage] = useState<string | null>(null);
  const [devPanelAnchor, setDevPanelAnchor] = useState<HTMLElement | null>(null);
  const [connectedSince, setConnectedSince] = useState<number | null>(null);
  const [reconnectCount, setReconnectCount] = useState(0);

  const editorHandleRef = useRef<LiveEditorHandle | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const initial = resolveInitialState(window.location.search);
    setExample(initial.example);
    setCode(initial.code);
  }, []);

  const handleLoadExample = useCallback((exampleId: string) => {
    const next = findExample(DEFAULT_FLAVOR, exampleId);
    if (!next) return;
    setExample(next);
    setCode(next.code);
  }, []);

  const handleCopyLink = useCallback(async () => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams();
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
  }, [example.id, code]);

  const editorKey = useMemo(() => `${DEFAULT_FLAVOR.id}:${example.id}`, [example.id]);

  const showVerificationPanel = !!flavorInfo?.verify && !!flavorInfo.verifyCommand;
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
    { label: 'Language', value: DEFAULT_FLAVOR.displayName, tooltip: `Language: ${DEFAULT_FLAVOR.displayName}` },
  ], []);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', minHeight: 480, bgcolor: 'var(--page-bg)' }}>
      <Toolbar
        logoSrc={logoSrc}
        examples={DEFAULT_FLAVOR.examples}
        onLoadExample={handleLoadExample}
        connectionStatus={status}
        onReconnect={() => editorHandleRef.current?.reconnect()}
        onDisconnect={() => editorHandleRef.current?.disconnect()}
        onCopyLink={handleCopyLink}
        copyConfirmation={copyConfirmation}
        colorModePreference={colorModePreference}
        onToggleColorMode={onToggleColorMode}
      />

      <Box sx={{ flex: '1 1 auto', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
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
            language={DEFAULT_FLAVOR.id}
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

        {showVerificationPanel && (
          <VerificationPanel
            editorHandle={editorHandleRef.current}
            verifyCommand={flavorInfo!.verifyCommand!}
            connected={status === 'connected'}
            onStatusMessage={handleVerificationStatus}
          />
        )}

        <StatusBar
          message={statusBarMessage}
          showProgress={statusBarShowProgress}
          infoItems={statusBarInfoItems}
          onInfoClick={(event) => setDevPanelAnchor(event.currentTarget)}
        />
        <DevInfoPanel
          anchorEl={devPanelAnchor}
          open={devPanelAnchor !== null}
          onClose={() => setDevPanelAnchor(null)}
          connectionStatus={status}
          language={DEFAULT_FLAVOR.displayName}
          connectedSince={connectedSince}
          reconnectCount={reconnectCount}
          editorHandle={editorHandleRef.current}
        />
      </Box>
    </Box>
  );
}
