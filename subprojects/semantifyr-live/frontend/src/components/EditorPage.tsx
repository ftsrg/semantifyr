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
import { decodeMaybeCompressedBase64Url, encodeCompressedBase64Url, normalizeBaseUrl } from '../lib/urls';
import type { ColorModePreference, ResolvedColorMode } from '../lib/theme';
import type { FlavorInfo } from '../lib/flavors';
import type { VerificationCaseState, WitnessValidationStatus } from '../lib/verification';
import { fetchPortfolios, type PortfolioInfo } from '../lib/portfolios';
import type {
  LiveEditorHandle,
  LiveEditorStatus,
} from './LiveEditor';
import Toolbar from './Toolbar';
import StatusBar, { type StatusBarInfoItem } from './StatusBar';
import DevInfoPanel from './DevInfoPanel';
import RightPane, { type RightPaneTab } from './RightPane';
import PaneSplitter from './PaneSplitter';
import InflightMonitor from './InflightMonitor';
import PortfolioSettings from './PortfolioSettings';
import { VerificationPanel, type VerificationPanelHandle } from './verification';

const LiveEditor = lazy(() => import('./LiveEditor'));

const DEFAULT_FLAVOR = LIVE_FLAVORS[0]!;
const DEFAULT_PORTFOLIO_ID = 'smart-full';
const RIGHT_PANE_WIDTH_KEY = 'semantifyr-right-pane-width';
const DEFAULT_RIGHT_PANE_WIDTH = 480;
const MIN_LEFT_WIDTH = 320;
const MIN_RIGHT_WIDTH = 280;
const VALIDATION_PORTFOLIO_KEY = 'semantifyr-validation-portfolio';
const AUTO_VALIDATE_KEY = 'semantifyr-auto-validate';
const DEFAULT_VALIDATION_PORTFOLIO_ID = 'smart-full';

const VERIFICATION_PANEL_HEIGHT_KEY = 'semantifyr-verification-panel-height';
const DEFAULT_VERIFICATION_PANEL_HEIGHT = 220;
// Header bar inside the panel takes ~32 px; the rest is the case-list body.
const VERIFICATION_PANEL_HEADER_HEIGHT = 32;
const MIN_VERIFICATION_PANEL_HEIGHT = 80;
// Editor needs to keep at least this many pixels above the splitter.
const MIN_EDITOR_AREA_HEIGHT = 200;

function readPersistedSize(key: string, fallback: number): number {
  if (typeof window === 'undefined') return fallback;
  try {
    const stored = window.localStorage.getItem(key);
    if (!stored) return fallback;
    const parsed = Number.parseInt(stored, 10);
    if (Number.isFinite(parsed) && parsed > 0) return parsed;
  } catch {
    /* ignore */
  }
  return fallback;
}

function readPersistedString(key: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback;
  try {
    return window.localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

function readPersistedBool(key: string, fallback: boolean): boolean {
  if (typeof window === 'undefined') return fallback;
  try {
    const stored = window.localStorage.getItem(key);
    if (stored === 'true') return true;
    if (stored === 'false') return false;
  } catch {
    /* ignore */
  }
  return fallback;
}

function persistString(key: string, value: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}

function persistBool(key: string, value: boolean): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(key, value ? 'true' : 'false');
  } catch {
    /* ignore */
  }
}

function persistSize(key: string, value: number): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(key, String(Math.round(value)));
  } catch {
    /* ignore */
  }
}

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

async function resolveInitialState(search: string): Promise<UrlState> {
  // Async because the `code` param is now gzip+base64url for new "Copy link" / "Open in new
  // tab" payloads (and plain base64url for legacy URLs). DecompressionStream is the cheapest
  // path that doesn't pull in a JS gzip dependency.
  const flavor = resolveInitialFlavor(search);
  const params = new URLSearchParams(search);
  const exampleParam = params.get('example');
  const codeParam = params.get('code');

  const decodedCode = codeParam ? await decodeMaybeCompressedBase64Url(codeParam) : null;

  if (exampleParam) {
    const example = findExample(flavor, exampleParam) ?? flavor.examples[0]!;
    return { flavor, example, code: decodedCode ?? example.code };
  }

  const example = flavor.examples[0]!;
  return { flavor, example, code: decodedCode ?? example.code };
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
  const [generatedSource, setGeneratedSource] = useState<string | null>(null);
  const [generatedSourceAt, setGeneratedSourceAt] = useState<number | null>(null);
  const [portfolios, setPortfolios] = useState<PortfolioInfo[]>([]);
  const [portfolioId, setPortfolioId] = useState<string>(DEFAULT_PORTFOLIO_ID);
  const [validationPortfolioId, setValidationPortfolioIdState] = useState<string>(() =>
    readPersistedString(VALIDATION_PORTFOLIO_KEY, DEFAULT_VALIDATION_PORTFOLIO_ID),
  );
  const [autoValidate, setAutoValidateState] = useState<boolean>(() =>
    readPersistedBool(AUTO_VALIDATE_KEY, true),
  );
  const handleValidationPortfolioChange = useCallback((id: string) => {
    setValidationPortfolioIdState(id);
    persistString(VALIDATION_PORTFOLIO_KEY, id);
  }, []);
  const handleAutoValidateChange = useCallback((enabled: boolean) => {
    setAutoValidateState(enabled);
    persistBool(AUTO_VALIDATE_KEY, enabled);
  }, []);
  const [rightPaneTab, setRightPaneTab] = useState<RightPaneTab>('generated');
  const [verificationCases, setVerificationCases] = useState<readonly VerificationCaseState[]>([]);
  const [selectedWitnessCaseId, setSelectedWitnessCaseId] = useState<string | null>(null);
  const [rightPaneWidth, setRightPaneWidth] = useState<number>(() =>
    readPersistedSize(RIGHT_PANE_WIDTH_KEY, DEFAULT_RIGHT_PANE_WIDTH),
  );
  const [verificationPanelHeight, setVerificationPanelHeight] = useState<number>(() =>
    readPersistedSize(VERIFICATION_PANEL_HEIGHT_KEY, DEFAULT_VERIFICATION_PANEL_HEIGHT),
  );

  const editorHandleRef = useRef<LiveEditorHandle | null>(null);
  const splitContainerRef = useRef<HTMLDivElement | null>(null);
  const pageContainerRef = useRef<HTMLDivElement | null>(null);
  const verificationPanelRef = useRef<VerificationPanelHandle | null>(null);

  const handleRightPaneWidthChange = useCallback((next: number) => {
    setRightPaneWidth(next);
    persistSize(RIGHT_PANE_WIDTH_KEY, next);
  }, []);

  const handleVerificationPanelHeightChange = useCallback((next: number) => {
    setVerificationPanelHeight(next);
    persistSize(VERIFICATION_PANEL_HEIGHT_KEY, next);
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    let cancelled = false;
    void resolveInitialState(window.location.search).then((initial) => {
      if (cancelled) return;
      setFlavor(initial.flavor);
      setExample(initial.example);
      setCode(initial.code);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Fetch the portfolio list once per backend.
  useEffect(() => {
    const { http } = normalizeBaseUrl(backendUrl);
    let cancelled = false;
    void fetchPortfolios(http)
      .then((list) => {
        if (cancelled) return;
        setPortfolios(list);
      })
      .catch(() => { /* non-fatal */ });
    return () => {
      cancelled = true;
    };
  }, [backendUrl]);

  const handleSelectFlavor = useCallback((flavorId: string) => {
    const next = findFlavor(flavorId);
    if (!next || next.id === flavor.id) return;
    const firstExample = next.examples[0]!;
    setFlavor(next);
    setExample(firstExample);
    setCode(firstExample.code);
    setGeneratedSource(null);
    setGeneratedSourceAt(null);
    setVerificationCases([]);
    setSelectedWitnessCaseId(null);
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
    setSelectedWitnessCaseId(null);
  }, [flavor]);

  const handleCopyLink = useCallback(async () => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams();
    params.set('mode', flavor.id);
    params.set('example', example.id);
    params.set('code', await encodeCompressedBase64Url(code));
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
    {
      label: 'Disclaimer',
      value: 'Research demo - for academic use only - EPL-2.0',
      tooltip: `${flavor.displayName} - click to open developer tools`,
    },
  ], [flavor.displayName]);

  // Generated source notification (server-emitted on Gamma / SysMLv2 frontend verifies). We
  // only auto-switch to the Generated tab on first arrival per session. Subsequent verifies
  // (especially "verify all", which fires the notification once per case with the same source)
  // would otherwise yank focus back from the Witness tab on every iteration.
  useEffect(() => {
    const handle = editorHandleRef.current;
    if (!handle) return;
    const dispose = handle.addNotificationListener('semantifyr/case/generatedSource', (params: unknown) => {
      const p = params as { source?: string; language?: string } | undefined;
      if (typeof p?.source !== 'string') return;
      setGeneratedSource((previous) => {
        if (previous === null) {
          setRightPaneTab('generated');
        }
        return p.source!;
      });
      setGeneratedSourceAt(Date.now());
    });
    return () => dispose?.();
  }, [editorKey]);

  // Mirror the verify outcome onto the editor as Monaco markers, so failed @VerificationCase
  // lines get squiggles and the ProblemsPill picks them up. Editor auto-clears on text changes.
  useEffect(() => {
    const handle = editorHandleRef.current;
    if (!handle) return;
    const markers = verificationCases.flatMap((cs) => {
      const severity = caseStatusToSeverity(cs.status);
      if (!severity) return [];
      return [{
        severity,
        startLine: cs.caseInfo.location.range.start.line,
        startColumn: cs.caseInfo.location.range.start.character,
        endLine: cs.caseInfo.location.range.end.line,
        endColumn: cs.caseInfo.location.range.end.character,
        message: cs.message ?? defaultMessageForStatus(cs.status, cs.caseInfo.label),
        source: 'semantifyr-verify',
      }];
    });
    handle.setVerifyCaseMarkers(markers);
  }, [verificationCases]);

  // The witness pane shows ONLY the case the user explicitly picked via the verification panel's
  // Show-witness button. If that case has no trace right now (re-running, fresh model, never
  // produced a witness), the witness tab disappears - we do NOT silently swap to another case.
  const witnessCaseState = useMemo<VerificationCaseState | null>(() => {
    if (!selectedWitnessCaseId) return null;
    const cs = verificationCases.find((c) => c.caseInfo.id === selectedWitnessCaseId);
    return cs?.trace ? cs : null;
  }, [verificationCases, selectedWitnessCaseId]);

  // Mid-run auto-default: when the user has not picked anything yet and a failure produces a
  // trace, latch onto it so the user sees the first counterexample as soon as it lands. Set
  // once; the user's later explicit picks take precedence.
  useEffect(() => {
    if (selectedWitnessCaseId) return;
    const firstFailure = verificationCases.find((cs) => cs.status === 'failed' && cs.trace !== undefined);
    if (firstFailure) setSelectedWitnessCaseId(firstFailure.caseInfo.id);
  }, [verificationCases, selectedWitnessCaseId]);

  // Auto-switch to the witness tab when a witness becomes visible.
  useEffect(() => {
    if (witnessCaseState && witnessCaseState.trace) {
      setRightPaneTab('witness');
    }
  }, [witnessCaseState?.caseInfo.id, witnessCaseState?.trace]);

  const witnessValidation: WitnessValidationStatus | undefined = witnessCaseState?.witnessValidation;

  // Resolve the user-facing portfolio name (e.g. "Auto", "Theta") for the witness pane. Falls
  // back to the raw portfolio id (or the verifier's reported backend id) when /api/portfolios
  // hasn't loaded yet or the chosen one isn't in the demo set.
  const verificationPortfolioLabel: string | undefined = useMemo(() => {
    if (!witnessCaseState) return undefined;
    const id = witnessCaseState.portfolioId ?? witnessCaseState.backendId;
    if (!id) return undefined;
    const match = portfolios.find((p) => p.id === id);
    return match?.displayName ?? id;
  }, [witnessCaseState, portfolios]);

  const handleOpenGeneratedInOxsts = useCallback(async (source: string) => {
    // The OXSTS source compresses ~6-12x with gzip+base64url, so even the larger compiled
    // models fit comfortably inside the server's 4 KB request line and every browser's URL
    // cap. No localStorage handoff needed; the new tab decodes the URL on load.
    if (typeof window === 'undefined') return;
    const targetFlavor = findFlavor('oxsts-with-gamma-library');
    if (!targetFlavor) return;
    const params = new URLSearchParams();
    params.set('mode', targetFlavor.id);
    params.set('code', await encodeCompressedBase64Url(source));
    const url = `${window.location.origin}${window.location.pathname}?${params.toString()}`;
    window.open(url, '_blank', 'noopener,noreferrer');
  }, []);

  const handleRevalidateWitness = useCallback(() => {
    if (!witnessCaseState) return;
    verificationPanelRef.current?.revalidate(witnessCaseState.caseInfo.id);
  }, [witnessCaseState]);

  const showRightPane = (generatedSource !== null) || (witnessCaseState?.trace != null);

  return (
    <Box
      ref={pageContainerRef}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100dvh',
        minHeight: { xs: 0, md: 480 },
        bgcolor: 'var(--page-bg)',
      }}
    >
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

      <Box
        ref={splitContainerRef}
        sx={{
          flex: '1 1 auto',
          minHeight: 0,
          display: 'flex',
          flexDirection: { xs: 'column', md: 'row' },
        }}
      >
        <Box
          sx={{
            flex: { xs: '1 1 50%', md: '1 1 auto' },
            minWidth: 0,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
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
        {showRightPane && (
          <>
            <Box sx={{ display: { xs: 'none', md: 'contents' } }}>
              <PaneSplitter
                containerRef={splitContainerRef}
                size={rightPaneWidth}
                onChange={handleRightPaneWidthChange}
                minBefore={MIN_LEFT_WIDTH}
                minAfter={MIN_RIGHT_WIDTH}
              />
            </Box>
            <Box
              sx={{
                flex: { xs: '1 1 50%', md: '0 0 auto' },
                width: { xs: '100%', md: rightPaneWidth },
                minWidth: { xs: 0, md: MIN_RIGHT_WIDTH },
                minHeight: 0,
                display: 'flex',
                flexDirection: 'column',
                borderTop: { xs: '1px solid var(--surface-border)', md: 'none' },
              }}
            >
              <RightPane
                generatedSource={generatedSource}
                generatedSourceLastUpdated={generatedSourceAt}
                onOpenGeneratedInOxsts={flavor.id === 'gamma' ? handleOpenGeneratedInOxsts : undefined}
                witnessCase={witnessCaseState?.caseInfo ?? null}
                witness={witnessCaseState?.trace ?? null}
                witnessValidation={witnessValidation}
                verificationPortfolioLabel={verificationPortfolioLabel}
                validating={witnessCaseState?.validating ?? false}
                canRevalidate={status === 'connected' && !!flavorInfo?.validateWitnessCommand}
                onRevalidate={handleRevalidateWitness}
                activeTab={rightPaneTab}
                onTabChange={setRightPaneTab}
              />
            </Box>
          </>
        )}
      </Box>

      <Box sx={{ flex: '0 0 auto', display: 'flex', flexDirection: 'column' }}>

        {showVerificationPanel && (
          <PaneSplitter
            containerRef={pageContainerRef}
            size={verificationPanelHeight}
            onChange={handleVerificationPanelHeightChange}
            orientation="horizontal"
            minBefore={MIN_EDITOR_AREA_HEIGHT}
            minAfter={MIN_VERIFICATION_PANEL_HEIGHT}
          />
        )}

        {showVerificationPanel && (
          <VerificationPanel
            ref={verificationPanelRef}
            editorHandle={editorHandleRef.current}
            verificationCommand={flavorInfo!.verificationCommand!}
            discoveryCommand={flavorInfo!.discoveryCommand!}
            validateWitnessCommand={flavorInfo!.validateWitnessCommand ?? undefined}
            connected={status === 'connected'}
            portfolioId={portfolioId}
            validationPortfolioId={validationPortfolioId}
            autoValidate={autoValidate}
            onAutoValidateChange={handleAutoValidateChange}
            caseListMaxHeight={Math.max(0, verificationPanelHeight - VERIFICATION_PANEL_HEADER_HEIGHT)}
            onStatusMessage={handleVerificationStatus}
            onCasesChange={setVerificationCases}
            onShowWitness={(caseId) => {
              setSelectedWitnessCaseId(caseId);
              setRightPaneTab('witness');
            }}
          />
        )}

        <StatusBar
          message={statusBarMessage}
          showProgress={statusBarShowProgress}
          infoItems={statusBarInfoItems}
          onInfoClick={() => setDevPanelOpen((prev) => !prev)}
          trailing={
            <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25 }}>
              <InflightMonitor editorHandle={editorHandleRef.current} connected={status === 'connected'} />
              {portfolios.length > 0 && (
                <PortfolioSettings
                  portfolios={portfolios}
                  verifyPortfolioId={portfolioId}
                  onVerifyPortfolioChange={setPortfolioId}
                  validationPortfolioId={flavorInfo?.validateWitnessCommand ? validationPortfolioId : null}
                  onValidationPortfolioChange={handleValidationPortfolioChange}
                />
              )}
            </Box>
          }
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

function caseStatusToSeverity(status: VerificationCaseState['status']): 'error' | 'warning' | null {
  switch (status) {
    case 'failed':
    case 'errored':
      return 'error';
    case 'inconclusive':
    case 'not_supported':
      return 'warning';
    default:
      return null;
  }
}

function defaultMessageForStatus(status: VerificationCaseState['status'], label: string): string {
  switch (status) {
    case 'failed':
      return `${label}: property fails on at least one execution. See the witness pane.`;
    case 'errored':
      return `${label}: verification raised an error. Check the verification panel for details.`;
    case 'inconclusive':
      return `${label}: the verifier could not produce a decisive verdict. Try a different portfolio.`;
    case 'not_supported':
      return `${label}: the chosen portfolio cannot transform this case. Pick another.`;
    default:
      return label;
  }
}

