/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import {
  findExample,
  findFlavor,
  liveFlavors,
  type LiveExample,
  type LiveFlavor,
} from '../examples';
import { decodeCompressedBase64Url, liveEditorUrl, parseLiveEditorUrl } from '@semantifyr/editor-common';
import type { ColorModePreference, ResolvedColorMode } from '../lib/util/colorMode';
import { createApi, type FlavorInfo, type PortfolioInfo } from '../lib/api';
import { DEFAULT_VALIDATION_PORTFOLIO_ID, type VerificationCaseState, type WitnessValidationStatus } from '../lib/verification';
import { casesToMarkers } from '../lib/session/caseMarkers';
import { findPortfolioLabel } from '../lib/verification/portfolioLabel';
import {
  boolCodec,
  sizeCodec,
  stringCodec,
  usePersistedState,
} from '../lib/hooks/usePersistedState';
import type {
  LiveEditorHandle,
  LiveEditorStatus,
} from './editor/LiveEditor';
import EditorShell from './shell/EditorShell';
import ConnectionBanner from './shell/ConnectionBanner';
import ConfirmDialog from './shell/ConfirmDialog';
import ErrorBoundary from './shell/ErrorBoundary';
import EditorLoadError from './editor/EditorLoadError';
import type { StatusBarInfoItem } from './shell/StatusBar';
import RightPanel, { type RightPanelTab } from './shell/RightPanel';
import RunningVerificationsTab from './verification/RunningVerificationsTab';
import WitnessTab from './witness/WitnessTab';
import MappedOxstsTab from './witness/MappedOxstsTab';
import PaneSplitter from './shell/PaneSplitter';
import ReopenRail from './shell/ReopenRail';
import { VerificationPanel, type VerificationPanelHandle } from './verification';
import { FONT_SIZE } from '../lib/util/theme';

const LiveEditor = lazy(() => import('./editor/LiveEditor'));

const RIGHT_PANE_WIDTH_KEY = 'semantifyr-right-pane-width';
const DEFAULT_RIGHT_PANE_WIDTH = 480;
const MIN_LEFT_WIDTH = 320;
const MIN_RIGHT_WIDTH = 280;
const VALIDATION_PORTFOLIO_KEY = 'semantifyr-validation-portfolio';
const AUTO_VALIDATE_KEY = 'semantifyr-auto-validate';

const VERIFICATION_PANEL_HEIGHT_KEY = 'semantifyr-verification-panel-height';
const VERIFICATION_PANEL_OPEN_KEY = 'semantifyr-verification-panel-open';
const RIGHT_PANEL_OPEN_KEY = 'semantifyr-right-panel-open';
const DEFAULT_VERIFICATION_PANEL_HEIGHT = 220;
const MIN_VERIFICATION_PANEL_HEIGHT = 80;
const MIN_EDITOR_AREA_HEIGHT = 120;

const COPY_CONFIRMATION_MS = 2000;
const FLAVORS_RETRY_MS = 5000;


interface Props {
  backendUrl: string;
  colorMode: ResolvedColorMode;
  colorModePreference: ColorModePreference;
  onToggleColorMode: () => void;
  embedded?: boolean;
}

interface UrlState {
  flavor: LiveFlavor;
  example: LiveExample;
  code: string;
  // code from a link that names no example
  shared: boolean;
}

interface LoadedPage {
  flavors: readonly [LiveFlavor, ...LiveFlavor[]];
  initial: UrlState;
}

async function resolveInitialState(search: string, flavors: readonly [LiveFlavor, ...LiveFlavor[]]): Promise<UrlState> {
  const parameters = parseLiveEditorUrl(search);
  const flavor = findFlavor(flavors, parameters.flavor) ?? flavors[0];
  const decodedCode = parameters.compressedCode ? await decodeCompressedBase64Url(parameters.compressedCode) : null;

  const fallback = flavor.examples[0];
  if (!fallback) {
    throw new Error(`flavor ${flavor.id} has no examples`);
  }
  if (parameters.example) {
    const example = findExample(flavor, parameters.example) ?? fallback;
    return { flavor, example, code: decodedCode ?? example.code, shared: false };
  }

  return { flavor, example: fallback, code: decodedCode ?? fallback.code, shared: decodedCode !== null };
}

function deriveConnectionMessage(status: LiveEditorStatus, statusInfo: string | null): string {
  switch (status) {
    case 'initializing': return statusInfo ?? 'Initializing...';
    case 'reconnecting': return statusInfo ?? 'Reconnecting...';
    case 'errored': return statusInfo ?? 'Connection error';
    case 'terminated': return statusInfo ?? 'Session ended';
    case 'disconnected': return 'Disconnected';
    case 'connected': return 'Ready';
  }
}

export default function EditorPage(props: Props): React.JSX.Element {
  const { backendUrl } = props;
  const [loaded, setLoaded] = useState<LoadedPage | null>(null);
  const [waiting, setWaiting] = useState(false);

  useEffect(() => {
    const api = createApi(backendUrl);
    let cancelled = false;
    let retryTimer: ReturnType<typeof setTimeout> | null = null;
    const load = async (): Promise<void> => {
      try {
        const flavors = liveFlavors(await api.fetchFlavors());
        const initial = await resolveInitialState(typeof window === 'undefined' ? '' : window.location.search, flavors);
        if (!cancelled) {
          setLoaded({ flavors, initial });
        }
      } catch {
        if (!cancelled) {
          setWaiting(true);
          retryTimer = setTimeout(() => {
            void load();
          }, FLAVORS_RETRY_MS);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
      if (retryTimer !== null) {
        clearTimeout(retryTimer);
      }
    };
  }, [backendUrl]);

  if (loaded === null) {
    return (
      <Box sx={{ height: '100dvh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2, bgcolor: 'var(--page-bg)' }}>
        <CircularProgress size={32} sx={{ color: 'text.secondary' }} />
        <Typography sx={{ color: 'text.secondary', fontSize: FONT_SIZE.md }}>
          {waiting ? 'Waiting for the Semantifyr server...' : 'Loading editor...'}
        </Typography>
      </Box>
    );
  }
  return <EditorWorkspace {...props} flavors={loaded.flavors} initial={loaded.initial} />;
}

function EditorWorkspace({
  backendUrl,
  colorMode,
  colorModePreference,
  onToggleColorMode,
  embedded = false,
  flavors,
  initial,
}: Props & LoadedPage): React.JSX.Element {
  const [flavor, setFlavor] = useState<LiveFlavor>(initial.flavor);
  const [example, setExample] = useState<LiveExample>(initial.example);
  const [code, setCode] = useState<string>(initial.code);
  const [shared, setShared] = useState(initial.shared);
  const [copyConfirmation, setCopyConfirmation] = useState<string | null>(null);
  const [pendingSwitch, setPendingSwitch] = useState<(() => void) | null>(null);

  const [status, setStatus] = useState<LiveEditorStatus>('initializing');
  const [statusInfo, setStatusInfo] = useState<string | null>(null);
  const [flavorInfo, setFlavorInfo] = useState<FlavorInfo | null>(null);
  const [verificationStatusMessage, setVerificationStatusMessage] = useState<string | null>(null);
  const [verificationBusy, setVerificationBusy] = useState(false);
  const [devPanelOpen, setDevPanelOpen] = useState(false);
  const [connectedSince, setConnectedSince] = useState<number | null>(null);
  const [reconnectCount, setReconnectCount] = useState(0);
  const [portfolios, setPortfolios] = useState<PortfolioInfo[]>([]);
  const [portfolioId, setPortfolioId] = useState<string>('');
  const [validationPortfolioId, handleValidationPortfolioChange] = usePersistedState(
    VALIDATION_PORTFOLIO_KEY,
    DEFAULT_VALIDATION_PORTFOLIO_ID,
    stringCodec,
  );
  const [autoValidate, handleAutoValidateChange] = usePersistedState(
    AUTO_VALIDATE_KEY,
    true,
    boolCodec,
  );
  const [verificationCases, setVerificationCases] = useState<readonly VerificationCaseState[]>([]);
  const [selectedWitnessCaseId, setSelectedWitnessCaseId] = useState<string | null>(null);
  const [rightPaneWidth, setRightPaneWidth] = usePersistedState(
    RIGHT_PANE_WIDTH_KEY,
    DEFAULT_RIGHT_PANE_WIDTH,
    sizeCodec,
  );
  const [verificationPanelHeight, setVerificationPanelHeight] = usePersistedState(
    VERIFICATION_PANEL_HEIGHT_KEY,
    DEFAULT_VERIFICATION_PANEL_HEIGHT,
    sizeCodec,
  );
  const [verificationPanelOpen, setVerificationPanelOpen] = usePersistedState(
    VERIFICATION_PANEL_OPEN_KEY,
    true,
    boolCodec,
  );
  const [rightPanelOpen, setRightPanelOpen] = usePersistedState(
    RIGHT_PANEL_OPEN_KEY,
    false,
    boolCodec,
  );

  const editorHandleRef = useRef<LiveEditorHandle | null>(null);
  const splitContainerRef = useRef<HTMLDivElement | null>(null);
  const pageContainerRef = useRef<HTMLDivElement | null>(null);
  const verificationPanelRef = useRef<VerificationPanelHandle | null>(null);
  const copyConfirmationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const api = createApi(backendUrl);
    let cancelled = false;
    void api.fetchPortfolios()
      .then((response) => {
        if (cancelled) return;
        setPortfolios(response.portfolios);
        setPortfolioId((current) => current || response.defaultPortfolio);
      })
      .catch(() => { /* non-fatal */ });
    return () => {
      cancelled = true;
    };
  }, [backendUrl]);

  // Monaco's model is the source of truth; `code` is only the initial seed.
  const currentEditorContent = useCallback((): string => {
    return editorHandleRef.current?.getCurrentCode() ?? code;
  }, [code]);

  const runOrConfirm = useCallback((apply: () => void) => {
    if (typeof window === 'undefined' || currentEditorContent() === example.code) {
      apply();
      return;
    }
    setPendingSwitch(() => apply);
  }, [currentEditorContent, example.code]);

  const handleSelectModel = useCallback((flavorId: string, exampleId: string) => {
    const nextFlavor = findFlavor(flavors, flavorId);
    if (!nextFlavor) return;
    const fallbackExample = nextFlavor.examples[0];
    if (!fallbackExample) return;
    const nextExample = findExample(nextFlavor, exampleId) ?? fallbackExample;
    if (nextFlavor.id === flavor.id && nextExample.id === example.id) return;
    runOrConfirm(() => {
      setFlavor(nextFlavor);
      setExample(nextExample);
      setCode(nextExample.code);
      setShared(false);
      setSelectedWitnessCaseId(null);
      if (typeof window !== 'undefined') {
        void liveEditorUrl(window.location.href, { flavor: nextFlavor.id, example: nextExample.id, embed: embedded }).then((url) => {
          window.history.replaceState(null, '', url);
        });
      }
    });
  }, [flavors, flavor.id, example.id, embedded, runOrConfirm]);

  const flashCopyConfirmation = useCallback((message: string) => {
    setCopyConfirmation(message);
    if (copyConfirmationTimerRef.current !== null) {
      clearTimeout(copyConfirmationTimerRef.current);
    }
    copyConfirmationTimerRef.current = setTimeout(() => {
      copyConfirmationTimerRef.current = null;
      setCopyConfirmation(null);
    }, COPY_CONFIRMATION_MS);
  }, []);

  useEffect(() => {
    return () => {
      if (copyConfirmationTimerRef.current !== null) {
        clearTimeout(copyConfirmationTimerRef.current);
        copyConfirmationTimerRef.current = null;
      }
    };
  }, []);

  const handleCopyLink = useCallback(async () => {
    if (typeof window === 'undefined') return;
    const content = currentEditorContent();
    const url = await liveEditorUrl(`${window.location.origin}${window.location.pathname}`, {
      flavor: flavor.id,
      example: shared ? undefined : example.id,
      code: shared || content !== example.code ? content : undefined,
    });
    try {
      await navigator.clipboard.writeText(url);
      flashCopyConfirmation('Link copied!');
    } catch {
      flashCopyConfirmation('Copy failed');
    }
  }, [shared, flavor.id, example.id, example.code, currentEditorContent, flashCopyConfirmation]);

  const editorKey = useMemo(() => `${flavor.id}:${example.id}`, [flavor.id, example.id]);

  const flavorSupportsVerification =
    !!flavorInfo?.verificationCommand && !!flavorInfo.discoveryCommand;
  const logoSrc = colorMode === 'dark' ? '/logo-full-dark.svg' : '/logo-full-light.svg';

  const statusBarMessage = verificationStatusMessage ?? deriveConnectionMessage(status, statusInfo);
  const statusBarShowProgress =
    verificationBusy ||
    status === 'initializing' ||
    status === 'reconnecting';

  const handleVerificationStatus = useCallback((message: string | null, busy: boolean) => {
    setVerificationStatusMessage(message);
    setVerificationBusy(busy);
  }, []);

  const statusBarInfoItems = useMemo((): StatusBarInfoItem[] => [
    {
      label: 'Disclaimer',
      value: 'Research demo - for academic use only - EPL-2.0',
      tooltip: `${flavor.displayName} - click to open developer tools`,
    },
  ], [flavor.displayName]);

  useEffect(() => {
    if (embedded || typeof document === 'undefined') {
      return;
    }
    const previous = document.title;
    document.title = `${shared ? 'Shared model' : example.label} - Semantifyr Live`;
    return () => {
      document.title = previous;
    };
  }, [embedded, shared, example.label]);

  // Only auto-retry from `errored`; a manual disconnect must stay off.
  useEffect(() => {
    if (typeof window === 'undefined' || status !== 'errored') {
      return;
    }
    const handleOnline = (): void => {
      editorHandleRef.current?.reconnect();
    };
    window.addEventListener('online', handleOnline);
    return () => {
      window.removeEventListener('online', handleOnline);
    };
  }, [status]);

  useEffect(() => {
    editorHandleRef.current?.setVerifyCaseMarkers(casesToMarkers(verificationCases));
  }, [verificationCases]);

  const witnessCaseState = useMemo<VerificationCaseState | null>(() => {
    if (!selectedWitnessCaseId) return null;
    const cs = verificationCases.find((c) => c.caseInfo.id === selectedWitnessCaseId);
    return cs?.trace ? cs : null;
  }, [verificationCases, selectedWitnessCaseId]);

  useEffect(() => {
    if (selectedWitnessCaseId) return;
    const firstFailure = verificationCases.find((cs) => cs.status === 'failed' && cs.trace !== undefined);
    if (firstFailure) setSelectedWitnessCaseId(firstFailure.caseInfo.id);
  }, [verificationCases, selectedWitnessCaseId]);

  const witnessValidation: WitnessValidationStatus | undefined = witnessCaseState?.witnessValidation;

  const verificationPortfolioLabel: string | undefined = useMemo(
    () =>
      findPortfolioLabel(portfolios, witnessCaseState?.portfolioId ?? witnessCaseState?.backendId),
    [witnessCaseState, portfolios],
  );

  const handleRevalidateWitness = useCallback(() => {
    if (!witnessCaseState) return;
    verificationPanelRef.current?.revalidate(witnessCaseState.caseInfo.id);
  }, [witnessCaseState]);

  const rightTabs = useMemo<readonly RightPanelTab[]>(() => {
    const tabs: RightPanelTab[] = [];
    if (flavorSupportsVerification) {
      tabs.push({
        id: 'runs',
        label: 'Verifications',
        content: (
          <RunningVerificationsTab
            api={editorHandleRef.current?.getApi() ?? null}
            connected={status === 'connected'}
            cases={verificationCases}
            portfolios={portfolios}
          />
        ),
      });
    }
    if (flavor.id === 'sysml') {
      tabs.push({
        id: 'oxsts',
        label: 'OXSTS',
        content: <MappedOxstsTab editorHandle={editorHandleRef.current} />,
      });
    }
    if (witnessCaseState?.trace != null) {
      tabs.push({
        id: 'witness',
        label: 'Witness',
        content: (
          <WitnessTab
            caseInfo={witnessCaseState.caseInfo}
            trace={witnessCaseState.trace}
            validation={witnessValidation}
            validationMessage={witnessCaseState.validationMessage}
            validating={witnessCaseState.validating ?? false}
            canRevalidate={status === 'connected' && !!flavorInfo?.validateWitnessCommand}
            onRevalidate={handleRevalidateWitness}
            verificationPortfolioLabel={verificationPortfolioLabel}
            editorHandle={editorHandleRef.current}
            witnessLanguageId={flavor.languageId}
          />
        ),
      });
    }
    return tabs;
  }, [
    flavorSupportsVerification,
    status,
    verificationCases,
    portfolios,
    witnessCaseState,
    witnessValidation,
    flavorInfo?.validateWitnessCommand,
    handleRevalidateWitness,
    verificationPortfolioLabel,
    flavor.id,
    flavor.languageId,
  ]);

  const [activeRightTabId, setActiveRightTabId] = useState<string | null>(null);

  const handleCloseRightPane = useCallback(() => {
    setRightPanelOpen(false);
  }, [setRightPanelOpen]);

  const handleShowWitness = useCallback((caseId: string) => {
    setSelectedWitnessCaseId(caseId);
    setRightPanelOpen(true);
    setActiveRightTabId('witness');
  }, [setRightPanelOpen]);

  const handleOpenVerificationsTab = useCallback(() => {
    setRightPanelOpen(true);
    setActiveRightTabId('runs');
  }, [setRightPanelOpen]);

  const showRightPane = rightPanelOpen;

  const body = (
    <>
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
            position: 'relative',
          }}
        >
          <ConnectionBanner
            status={status}
            statusInfo={statusInfo}
            onReconnect={() => editorHandleRef.current?.reconnect()}
          />
          <ErrorBoundary label="editor" fallback={<EditorLoadError />}>
            <Suspense
              fallback={
                <Box sx={{ flex: '1 1 auto', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2, bgcolor: 'var(--page-bg)' }}>
                  <CircularProgress size={32} sx={{ color: 'text.secondary' }} />
                  <Typography sx={{ color: 'text.secondary', fontSize: FONT_SIZE.md }}>
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
          </ErrorBoundary>
        </Box>
        {showRightPane && (
          <>
            <Box sx={{ display: { xs: 'none', md: 'contents' } }}>
              <PaneSplitter
                containerRef={splitContainerRef}
                size={rightPaneWidth}
                onChange={setRightPaneWidth}
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
              <RightPanel
                tabs={rightTabs}
                activeTabId={activeRightTabId}
                onActiveTabChange={setActiveRightTabId}
                onClose={handleCloseRightPane}
              />
            </Box>
          </>
        )}
        {!showRightPane && (
          <ReopenRail
            orientation="vertical"
            label="Side panel"
            ariaLabel="Show side panel"
            onClick={() => { setRightPanelOpen(true); }}
          />
        )}
      </Box>

      <Box sx={{ flex: '0 0 auto', display: 'flex', flexDirection: 'column' }}>

        {flavorSupportsVerification && verificationPanelOpen && (
          <PaneSplitter
            containerRef={pageContainerRef}
            size={verificationPanelHeight}
            onChange={setVerificationPanelHeight}
            orientation="horizontal"
            minBefore={MIN_EDITOR_AREA_HEIGHT}
            minAfter={MIN_VERIFICATION_PANEL_HEIGHT}
          />
        )}

        {flavorSupportsVerification && flavorInfo.verificationCommand && flavorInfo.discoveryCommand && (
          // Hidden, not unmounted, so verification state survives a close/reopen.
          <Box sx={{ display: verificationPanelOpen ? 'flex' : 'none', flexDirection: 'column', flex: '0 0 auto' }}>
            <VerificationPanel
              key={`${flavor.id}:${example.id}`}
              ref={verificationPanelRef}
              editorHandle={editorHandleRef.current}
              verificationCommand={flavorInfo.verificationCommand}
              discoveryCommand={flavorInfo.discoveryCommand}
              validateWitnessCommand={flavorInfo.validateWitnessCommand ?? undefined}
              validateWitnessByCase={flavorInfo.validateWitnessByCase}
              connected={status === 'connected'}
              portfolioId={portfolioId}
              validationPortfolioId={validationPortfolioId}
              autoValidate={autoValidate}
              onAutoValidateChange={handleAutoValidateChange}
              panelHeight={verificationPanelHeight}
              onClose={() => { setVerificationPanelOpen(false); }}
              onStatusMessage={handleVerificationStatus}
              onCasesChange={setVerificationCases}
              onShowWitness={handleShowWitness}
              portfolios={portfolios}
            />
          </Box>
        )}

        {flavorSupportsVerification && !verificationPanelOpen && (
          <ReopenRail
            orientation="horizontal"
            label="Show verification panel"
            ariaLabel="Show verification panel"
            onClick={() => { setVerificationPanelOpen(true); }}
          />
        )}
      </Box>
    </>
  );

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
      {embedded ? body : (
        <EditorShell
          logoSrc={logoSrc}
          flavors={flavors}
          currentFlavorId={flavor.id}
          currentExampleId={example.id}
          onSelectModel={handleSelectModel}
          onCopyLink={() => { void handleCopyLink(); }}
          copyConfirmation={copyConfirmation}
          connectionStatus={status}
          onReconnect={() => editorHandleRef.current?.reconnect()}
          onDisconnect={() => { void editorHandleRef.current?.disconnect(); }}
          statusBarMessage={statusBarMessage}
          statusBarShowProgress={statusBarShowProgress}
          statusBarInfoItems={statusBarInfoItems}
          onStatusInfoClick={() => { setDevPanelOpen((prev) => !prev); }}
          onOpenVerificationsTab={handleOpenVerificationsTab}
          portfolios={portfolios}
          portfolioId={portfolioId}
          onPortfolioChange={setPortfolioId}
          validationPortfolioId={validationPortfolioId}
          onValidationPortfolioChange={handleValidationPortfolioChange}
          flavorInfo={flavorInfo}
          colorModePreference={colorModePreference}
          onToggleColorMode={onToggleColorMode}
          devPanelOpen={devPanelOpen}
          onCloseDevPanel={() => { setDevPanelOpen(false); }}
          language={flavor.displayName}
          connectedSince={connectedSince}
          reconnectCount={reconnectCount}
          editorHandle={editorHandleRef.current}
          backendUrl={backendUrl}
        >
          {body}
        </EditorShell>
      )}
      <ConfirmDialog
        open={pendingSwitch !== null}
        title="Discard unsaved edits?"
        message="You have unsaved edits in the editor. Switching discards them."
        confirmLabel="Discard and switch"
        onCancel={() => { setPendingSwitch(null); }}
        onConfirm={() => {
          pendingSwitch?.();
          setPendingSwitch(null);
        }}
      />
    </Box>
  );
}


