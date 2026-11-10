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
import { FONT_SIZE } from '../lib/util/theme';
import { liveEditorUrl } from '@semantifyr/editor-common';
import type { ColorModePreference, ResolvedColorMode } from '../lib/util/colorMode';
import type { VerificationCaseState } from '../lib/verification';
import { findPortfolioLabel } from '../lib/verification/portfolioLabel';
import { attachReadonlyEditor, type ReadonlyEditorOpener } from '../lib/session/monaco';
import type { DerivedDocument } from '../lib/language/types';
import { changeSettings } from '../lib/stores/settings';
import { constantStore } from '../lib/stores/store';
import { useStore } from '../lib/stores/useStore';
import { useCurrentVerification, useLive, useRuntime, useRuntimeStore } from '../lib/workspace/liveContext';
import type { DocumentRecord } from '../lib/workspace/live';
import { statusLine } from '../lib/workspace/statusLine';
import type { LiveFlavor } from '../examples';
import EditorShell from './shell/EditorShell';
import ConnectionBanner from './shell/ConnectionBanner';
import ErrorBoundary from './shell/ErrorBoundary';
import EditorLoadError from './editor/EditorLoadError';
import EditorLoading from './editor/EditorLoading';
import { editorStatusOf } from './shell/editorStatus';
import type { StatusBarInfoItem } from './shell/StatusBar';
import RightPanel, { type RightPanelTab } from './shell/RightPanel';
import RunningVerificationsTab from './verification/RunningVerificationsTab';
import WitnessTab from './witness/WitnessTab';
import MappedOxstsTab from './witness/MappedOxstsTab';
import PaneSplitter from './shell/PaneSplitter';
import ReopenRail from './shell/ReopenRail';
import { VerificationPanel } from './verification';

const LiveEditor = lazy(() => import('./editor/LiveEditor'));

const MIN_LEFT_WIDTH = 320;
const MIN_RIGHT_WIDTH = 280;
const MIN_VERIFICATION_PANEL_HEIGHT = 80;
const MIN_EDITOR_AREA_HEIGHT = 120;

const COPY_CONFIRMATION_MS = 2000;

const NO_DERIVED_DOCUMENTS = constantStore<readonly DerivedDocument[]>([]);

interface Props {
  backendUrl: string;
  colorMode: ResolvedColorMode;
  colorModePreference: ColorModePreference;
  onToggleColorMode: () => void;
  embedded?: boolean;
}

export default function EditorPage(props: Props): React.JSX.Element {
  const live = useLive();
  const catalogStatus = useStore(live.catalog, (catalog) => {
    return catalog.status;
  });
  const flavor = useStore(live.workspace, (workspace) => {
    return workspace.flavor;
  });
  const documentKey = useStore(live.workspace, (workspace) => {
    return workspace.documentKey;
  });
  const document = useStore(live.documents, (documents) => {
    return documentKey === null ? undefined : documents.get(documentKey);
  });

  if (catalogStatus !== 'ready' || !flavor || !document) {
    return <EditorLoading message={catalogStatus === 'waiting' ? 'Waiting for the Semantifyr server...' : 'Loading editor...'} />;
  }
  return <EditorWorkspace {...props} flavor={flavor} document={document} />;
}

function EditorWorkspace({
  backendUrl,
  colorMode,
  colorModePreference,
  onToggleColorMode,
  embedded = false,
  flavor,
  document,
}: Props & { flavor: LiveFlavor; document: DocumentRecord }): React.JSX.Element {
  const live = useLive();
  const example = document.example;
  const shared = document.shared;
  const registry = useStore(live.catalog, (catalog) => {
    return catalog.registry;
  });
  const portfolios = useStore(live.catalog, (catalog) => {
    return catalog.portfolios;
  });
  const settings = useStore(live.settings);
  const connection = useStore(live.connection);
  const initialization = useStore(live.initialization);
  const verificationState = useCurrentVerification(live);
  const selectedCaseId = useStore(live.selection);
  const runtime = useRuntime(live);
  const derivedDocuments = useRuntimeStore(live, (current) => {
    return current.derivedDocuments;
  }, NO_DERIVED_DOCUMENTS);

  const [copyConfirmation, setCopyConfirmation] = useState<string | null>(null);
  const [devPanelOpen, setDevPanelOpen] = useState(false);

  const splitContainerRef = useRef<HTMLDivElement | null>(null);
  const pageContainerRef = useRef<HTMLDivElement | null>(null);
  const copyConfirmationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // both stores it derives from are subscribed above
  const portfolioId = live.verificationPortfolioId() ?? '';
  const status = editorStatusOf(connection);
  const verificationCases = verificationState.cases;
  const rightPaneWidth = settings.sidePanelWidth;
  const verificationPanelHeight = settings.resultsPanelHeight;
  const verificationPanelOpen = settings.resultsPanelOpen;
  const rightPanelOpen = settings.sidePanelOpen;
  const flavors = registry?.flavors ?? [flavor];

  const handleSelectModel = useCallback((flavorId: string, exampleId: string) => {
    live.selectModel(flavorId, exampleId);
    if (typeof window !== 'undefined') {
      void liveEditorUrl(window.location.href, { flavor: flavorId, example: exampleId, embed: embedded }).then((url) => {
        window.history.replaceState(null, '', url);
      });
    }
  }, [live, embedded]);

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
    const content = live.currentText() ?? document.text;
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
  }, [live, shared, flavor.id, example.id, example.code, document.text, flashCopyConfirmation]);

  const logoSrc = colorMode === 'dark' ? '/logo-full-dark.svg' : '/logo-full-light.svg';

  const { message: statusBarMessage, busy: statusBarShowProgress } = statusLine(verificationState, connection, initialization);

  const statusBarInfoItems = useMemo((): StatusBarInfoItem[] => [
    {
      label: 'Disclaimer',
      value: 'Research demo - for academic use only - EPL-2.0',
      tooltip: `${flavor.displayName} - click to open developer tools`,
    },
  ], [flavor.displayName]);

  useEffect(() => {
    if (embedded || typeof window === 'undefined') {
      return;
    }
    const previous = window.document.title;
    window.document.title = `${shared ? 'Shared model' : example.label} - Semantifyr Live`;
    return () => {
      window.document.title = previous;
    };
  }, [embedded, shared, example.label]);

  const witnessCaseState = useMemo<VerificationCaseState | null>(() => {
    if (!selectedCaseId) return null;
    const selected = verificationCases.find((caseState) => {
      return caseState.caseInfo.id === selectedCaseId;
    });
    return selected?.trace ? selected : null;
  }, [verificationCases, selectedCaseId]);

  const verificationPortfolioLabel: string | undefined = useMemo(() => {
    return findPortfolioLabel(portfolios, witnessCaseState?.portfolioId ?? witnessCaseState?.backendId);
  }, [witnessCaseState, portfolios]);

  const openReadonlyEditor = useMemo<ReadonlyEditorOpener | null>(() => {
    if (!runtime) {
      return null;
    }
    return (host, uri, language) => {
      return attachReadonlyEditor(host, runtime.api, uri, language);;
    }
  }, [runtime]);

  const rightTabs = useMemo<readonly RightPanelTab[]>(() => {
    const tabs: RightPanelTab[] = [
      {
        id: 'runs',
        label: 'Verifications',
        content: (
          <RunningVerificationsTab
            api={runtime?.api ?? null}
            connected={status === 'connected'}
            cases={verificationCases}
            portfolios={portfolios}
          />
        ),
      },
    ];
    for (const derived of derivedDocuments) {
      tabs.push({
        id: derived.id,
        label: derived.label,
        content: <MappedOxstsTab document={derived} openReadonlyEditor={openReadonlyEditor} />,
      });
    }
    if (witnessCaseState?.trace != null) {
      const caseId = witnessCaseState.caseInfo.id;
      tabs.push({
        id: 'witness',
        label: 'Witness',
        content: (
          <WitnessTab
            caseInfo={witnessCaseState.caseInfo}
            trace={witnessCaseState.trace}
            validation={witnessCaseState.witnessValidation}
            validationMessage={witnessCaseState.validationMessage}
            validating={witnessCaseState.validating ?? false}
            canRevalidate={status === 'connected' && !!flavor.validateWitnessCommand}
            onRevalidate={() => {
              live.validateWitness(caseId);
            }}
            verificationPortfolioLabel={verificationPortfolioLabel}
            openReadonlyEditor={openReadonlyEditor}
            witnessLanguageId={flavor.languageId}
          />
        ),
      });
    }
    return tabs;
  }, [
    live,
    runtime,
    status,
    verificationCases,
    portfolios,
    derivedDocuments,
    openReadonlyEditor,
    witnessCaseState,
    verificationPortfolioLabel,
    flavor.validateWitnessCommand,
    flavor.languageId,
  ]);

  const [activeRightTabId, setActiveRightTabId] = useState<string | null>(null);

  const setRightPanelOpen = useCallback((open: boolean) => {
    changeSettings(live.settings, { sidePanelOpen: open });
  }, [live]);

  const handleCloseRightPane = useCallback(() => {
    setRightPanelOpen(false);
  }, [setRightPanelOpen]);

  const handleShowWitness = useCallback((caseId: string) => {
    live.selectCase(caseId);
    setRightPanelOpen(true);
    setActiveRightTabId('witness');
  }, [live, setRightPanelOpen]);

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
            statusInfo={connection.reason}
            onReconnect={live.reconnect}
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
                key={flavor.id}
                languageId={flavor.languageId}
                documentUri={document.uri}
                initialCode={document.text}
                colorMode={colorMode}
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
                onChange={(width) => {
                  changeSettings(live.settings, { sidePanelWidth: width });
                }}
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

        {verificationPanelOpen && (
          <PaneSplitter
            containerRef={pageContainerRef}
            size={verificationPanelHeight}
            onChange={(height) => {
              changeSettings(live.settings, { resultsPanelHeight: height });
            }}
            orientation="horizontal"
            minBefore={MIN_EDITOR_AREA_HEIGHT}
            minAfter={MIN_VERIFICATION_PANEL_HEIGHT}
          />
        )}

        <Box sx={{ display: verificationPanelOpen ? 'flex' : 'none', flexDirection: 'column', flex: '0 0 auto' }}>
          <VerificationPanel
            panelHeight={verificationPanelHeight}
            onClose={() => {
              changeSettings(live.settings, { resultsPanelOpen: false });
            }}
            onShowWitness={handleShowWitness}
          />
        </Box>

        {!verificationPanelOpen && (
          <ReopenRail
            orientation="horizontal"
            label="Show verification panel"
            ariaLabel="Show verification panel"
            onClick={() => {
              changeSettings(live.settings, { resultsPanelOpen: true });
            }}
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
          onReconnect={live.reconnect}
          onDisconnect={() => {
            void live.disconnect();
          }}
          statusBarMessage={statusBarMessage}
          statusBarShowProgress={statusBarShowProgress}
          statusBarInfoItems={statusBarInfoItems}
          onStatusInfoClick={() => { setDevPanelOpen((prev) => !prev); }}
          onOpenVerificationsTab={handleOpenVerificationsTab}
          portfolios={portfolios}
          portfolioId={portfolioId}
          onPortfolioChange={(id) => {
            changeSettings(live.settings, { verificationPortfolioId: id });
          }}
          validationPortfolioId={settings.validationPortfolioId}
          onValidationPortfolioChange={(id) => {
            changeSettings(live.settings, { validationPortfolioId: id });
          }}
          validates={!!flavor.validateWitnessCommand}
          colorModePreference={colorModePreference}
          onToggleColorMode={onToggleColorMode}
          devPanelOpen={devPanelOpen}
          onCloseDevPanel={() => { setDevPanelOpen(false); }}
          language={flavor.displayName}
          connectedSince={connection.connectedSince}
          reconnectCount={connection.reconnectCount}
          runtime={runtime}
          backendUrl={backendUrl}
        >
          {body}
        </EditorShell>
      )}
    </Box>
  );
}
