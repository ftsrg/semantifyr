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
import { decodeCompressedBase64Url } from '../lib/api/urls';
import { buildShareableUrl } from '../lib/util/sharing';
import type { ColorModePreference, ResolvedColorMode } from '../lib/util/colorMode';
import { createApi, type FlavorInfo, type PortfolioInfo } from '../lib/api';
import type { VerificationCaseState, WitnessValidationStatus } from '../lib/verification';
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
import { type StatusBarInfoItem } from './shell/StatusBar';
import RightPanel, { type RightPanelTab } from './shell/RightPanel';
import RunningVerificationsTab from './verification/RunningVerificationsTab';
import WitnessTab from './witness/WitnessTab';
import PaneSplitter from './shell/PaneSplitter';
import ReopenRail from './shell/ReopenRail';
import { VerificationPanel, type VerificationPanelHandle } from './verification';
import { FONT_SIZE } from '../lib/util/theme';

const LiveEditor = lazy(() => import('./editor/LiveEditor'));

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
const VERIFICATION_PANEL_OPEN_KEY = 'semantifyr-verification-panel-open';
const RIGHT_PANEL_OPEN_KEY = 'semantifyr-right-panel-open';
const DEFAULT_VERIFICATION_PANEL_HEIGHT = 220;
const MIN_VERIFICATION_PANEL_HEIGHT = 80;
// Editor keeps at least this many pixels above the splitter so the user can never lose the
// editing surface entirely; the bottom panel can grow up to (containerHeight - this).
const MIN_EDITOR_AREA_HEIGHT = 120;

const COPY_CONFIRMATION_MS = 2000;

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
  /**
   * Embedded entry point: hide toolbar, status bar, and developer panel so the body can be
   * iframed by another site. The verification + right panels still work normally.
   */
  embedded?: boolean;
}

interface UrlState {
  flavor: LiveFlavor;
  example: LiveExample;
  code: string;
}

async function resolveInitialState(search: string): Promise<UrlState> {
  // Async because the `code` param is gzip+base64url. DecompressionStream is the cheapest
  // path that doesn't pull in a JS gzip dependency.
  const flavor = resolveInitialFlavor(search);
  const params = new URLSearchParams(search);
  const exampleParam = params.get('example');
  const codeParam = params.get('code');

  const decodedCode = codeParam ? await decodeCompressedBase64Url(codeParam) : null;

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
  embedded = false,
}: Props): React.JSX.Element {
  const [flavor, setFlavor] = useState<LiveFlavor>(DEFAULT_FLAVOR);
  const [example, setExample] = useState<LiveExample>(DEFAULT_FLAVOR.examples[0]!);
  const [code, setCode] = useState<string>(DEFAULT_FLAVOR.examples[0]!.code);
  const [copyConfirmation, setCopyConfirmation] = useState<string | null>(null);
  // When a flavor/example switch would discard unsaved edits, the action is parked here and
  // the ConfirmDialog gates it; null when there is nothing pending.
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
  const [portfolioId, setPortfolioId] = useState<string>(DEFAULT_PORTFOLIO_ID);
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
    const api = createApi(backendUrl);
    let cancelled = false;
    void api.fetchPortfolios()
      .then((list) => {
        if (cancelled) return;
        setPortfolios(list);
      })
      .catch(() => { /* non-fatal */ });
    return () => {
      cancelled = true;
    };
  }, [backendUrl]);

  // The Monaco model is the source of truth for the editor's current text; the React `code`
  // state is the INITIAL load only. Read directly so the dirty check sees user keystrokes.
  const currentEditorContent = useCallback((): string => {
    return editorHandleRef.current?.getCurrentCode() ?? code;
  }, [code]);

  // Run `apply` immediately when the editor has no unsaved edits; otherwise stash it as a
  // pending action and let the ConfirmDialog (rendered below) gate it. Replaces a blocking
  // `window.confirm`, which can't be done with an async MUI dialog.
  const runOrConfirm = useCallback((apply: () => void) => {
    if (typeof window === 'undefined' || currentEditorContent() === example.code) {
      apply();
      return;
    }
    setPendingSwitch(() => apply);
  }, [currentEditorContent, example.code]);

  // Switch flavor and/or example in one step (the model picker hands both back together).
  // A flavor change also wipes the verification state; an example-only change keeps it for
  // the cases that still discover.
  const handleSelectModel = useCallback((flavorId: string, exampleId: string) => {
    const nextFlavor = findFlavor(flavorId);
    if (!nextFlavor) return;
    const nextExample = findExample(nextFlavor, exampleId) ?? nextFlavor.examples[0]!;
    if (nextFlavor.id === flavor.id && nextExample.id === example.id) return;
    const flavorChanged = nextFlavor.id !== flavor.id;
    runOrConfirm(() => {
      setFlavor(nextFlavor);
      setExample(nextExample);
      setCode(nextExample.code);
      setSelectedWitnessCaseId(null);
      if (flavorChanged) {
        setVerificationCases([]);
      }
      if (typeof window !== 'undefined') {
        const url = new URL(window.location.href);
        url.searchParams.set('mode', nextFlavor.id);
        url.searchParams.set('example', nextExample.id);
        url.searchParams.delete('code');
        window.history.replaceState(null, '', url.toString());
      }
    });
  }, [flavor.id, example.id, runOrConfirm]);

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
    // Read from the Monaco model so the link reflects the user's actual keystrokes, not just
    // the initial example body.
    const url = await buildShareableUrl(
      `${window.location.origin}${window.location.pathname}`,
      {
        flavorId: flavor.id,
        exampleId: example.id,
        code: currentEditorContent(),
        // When the editor is still on the bundled example, drop the code param: the loader
        // would just override it with example.code anyway.
        exampleCode: example.code,
      },
    );
    try {
      await navigator.clipboard.writeText(url);
      flashCopyConfirmation('Link copied!');
    } catch {
      flashCopyConfirmation('Copy failed');
    }
  }, [flavor.id, example.id, currentEditorContent, flashCopyConfirmation]);

  const editorKey = useMemo(() => `${flavor.id}:${example.id}`, [flavor.id, example.id]);

  // The flavor exposes verify+discovery commands. The panel stays MOUNTED whenever this is
  // true so verifyState (cases, verdicts, witnesses) survives a close-then-reopen; closing
  // only hides the panel and surfaces a thin reopen button just above the status bar. The
  // open/closed choice is persisted across reloads.
  const flavorSupportsVerification =
    !!flavorInfo?.verificationCommand && !!flavorInfo.discoveryCommand;
  const logoSrc = colorMode === 'dark' ? '/logo-full-dark.svg' : '/logo-full-light.svg';

  const statusBarMessage = verificationStatusMessage ?? deriveConnectionMessage(status, statusInfo);
  // Linear-progress is wired to "actually doing async work right now": connection setup OR
  // a verify currently running. Terminal verdicts hold the message but stop the spinner.
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

  // Reflect the current example in the browser tab title (the flavor is already visible in the
  // toolbar, and one flavor's display name *is* "Semantifyr", so folding it in here doubles up).
  // Skipped in embedded mode so we never overwrite the host page's title.
  useEffect(() => {
    if (embedded || typeof document === 'undefined') {
      return;
    }
    const previous = document.title;
    document.title = `${example.label} - Semantifyr Live`;
    return () => {
      document.title = previous;
    };
  }, [embedded, example.label]);

  // When the browser regains connectivity after an errored session, retry automatically so the
  // user does not have to find the reconnect button. A user-initiated `disconnected` state is
  // left alone - they turned it off on purpose.
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

  // Mirror the verification outcome onto the editor as Monaco markers, so failed @VerificationCase
  // lines get squiggles and the ProblemsPill picks them up. Editor auto-clears on text changes.
  useEffect(() => {
    editorHandleRef.current?.setVerifyCaseMarkers(casesToMarkers(verificationCases));
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

  const witnessValidation: WitnessValidationStatus | undefined = witnessCaseState?.witnessValidation;

  // Resolve the user-facing portfolio name (e.g. "Auto", "Theta") for the witness pane. Falls
  // back to the raw portfolio id (or the verifier's reported backend id) when /api/portfolios
  // hasn't loaded yet or the chosen one isn't in the demo set.
  const verificationPortfolioLabel: string | undefined = useMemo(
    () =>
      findPortfolioLabel(portfolios, witnessCaseState?.portfolioId ?? witnessCaseState?.backendId),
    [witnessCaseState, portfolios],
  );

  const handleRevalidateWitness = useCallback(() => {
    if (!witnessCaseState) return;
    verificationPanelRef.current?.revalidate(witnessCaseState.caseInfo.id);
  }, [witnessCaseState]);

  // Right-panel tab construction. The Running Verifications tab is always available when the
  // flavor supports verification - the user benefits from a single place to see what's queued,
  // running, and recently completed. The Witness tab appears only when a witness has been
  // selected. Future tabs (Compiled OXSTS, Instances) plug in here.
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
    if (witnessCaseState?.trace != null) {
      tabs.push({
        id: 'witness',
        label: 'Witness',
        content: (
          <WitnessTab
            caseInfo={witnessCaseState.caseInfo}
            trace={witnessCaseState.trace}
            validation={witnessValidation}
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
    flavor.languageId,
  ]);

  const [activeRightTabId, setActiveRightTabId] = useState<string | null>(null);

  // Closes the right panel without unmounting it; the panel keeps its tab content state so a
  // subsequent reopen shows the same witness, the same scroll position, etc. Data sources
  // (e.g. the witness selection) stay populated so reopening surfaces them again.
  const handleCloseRightPane = useCallback(() => {
    setRightPanelOpen(false);
  }, [setRightPanelOpen]);

  // Showing a witness should also pop the panel open if the user previously closed it.
  const handleShowWitness = useCallback((caseId: string) => {
    setSelectedWitnessCaseId(caseId);
    setRightPanelOpen(true);
    setActiveRightTabId('witness');
  }, [setRightPanelOpen]);

  // Status-bar's active-verifications indicator opens the side panel's Verifications tab. A
  // single source of truth for the data, with the indicator acting as a glance + entry point.
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
          {/* If the editor *bundle* never arrives (offline first visit, stale chunk, network
              blip), the lazy import rejects - the boundary swaps in a reload card instead of
              letting the failure take down the whole page. */}
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
            onClick={() => setRightPanelOpen(true)}
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

        {flavorSupportsVerification && (
          // Wrapper hides the panel via display: 'none' instead of unmounting so verifyState
          // (cases, verdicts, witnesses) survives a close-then-reopen.
          <Box sx={{ display: verificationPanelOpen ? 'flex' : 'none', flexDirection: 'column', flex: '0 0 auto' }}>
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
              panelHeight={verificationPanelHeight}
              onClose={() => setVerificationPanelOpen(false)}
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
            onClick={() => setVerificationPanelOpen(true)}
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
          flavors={LIVE_FLAVORS}
          currentFlavorId={flavor.id}
          currentExampleId={example.id}
          onSelectModel={handleSelectModel}
          onCopyLink={handleCopyLink}
          copyConfirmation={copyConfirmation}
          connectionStatus={status}
          onReconnect={() => editorHandleRef.current?.reconnect()}
          onDisconnect={() => editorHandleRef.current?.disconnect()}
          statusBarMessage={statusBarMessage}
          statusBarShowProgress={statusBarShowProgress}
          statusBarInfoItems={statusBarInfoItems}
          onStatusInfoClick={() => setDevPanelOpen((prev) => !prev)}
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
          onCloseDevPanel={() => setDevPanelOpen(false)}
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
        onCancel={() => setPendingSwitch(null)}
        onConfirm={() => {
          pendingSwitch?.();
          setPendingSwitch(null);
        }}
      />
    </Box>
  );
}


