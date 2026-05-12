/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Divider from '@mui/material/Divider';
import CircularProgress from '@mui/material/CircularProgress';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import CloseIcon from '@mui/icons-material/Close';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import MyLocationOutlinedIcon from '@mui/icons-material/MyLocationOutlined';
import Badge from '@mui/material/Badge';
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined';

import {
  dispatchAutoValidation,
  discoverVerificationCases,
  runAllVerifications,
  verifySingleCase,
  type VerificationCaseLocation,
  type VerificationCaseState,
  type VerificationState,
  type RunVerificationHandle,
} from '../../lib/verification';
import type { LiveEditorHandle } from '../editor/LiveEditor';
import VerifyButton from './VerifyButton';
import RefreshButton from './RefreshButton';
import ProblemsPill from './ProblemsPill';
import { CaseStatusIcon, SummaryCounts, SummaryStatusIcon } from './StatusDisplay';
import { formatIsoDurationDetailed } from '../../lib/util/duration';
import { buildMetricsTooltip, isMeaningfulDuration } from '../../lib/verification/metricsTooltip';
import { witnessIconDescriptor } from '../../lib/verification/witnessIcon';
import { findPortfolioLabel } from '../../lib/verification/portfolioLabel';
import { PortfolioInfo } from '../../lib/api'
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface MetricsPillSource {
  verb: 'verified' | 'validated';
  totalDuration: string;
  metrics: VerificationCaseState['metrics'];
  portfolioLabel?: string | undefined;
  backendId?: string | undefined;
}


interface Props {
  editorHandle: LiveEditorHandle | null;
  verificationCommand: string;
  discoveryCommand: string;
  validateWitnessCommand?: string | undefined;
  connected: boolean;
  portfolioId: string;
  validationPortfolioId: string;
  autoValidate: boolean;
  onAutoValidateChange?: (enabled: boolean) => void;
  panelHeight: number;
  onClose: () => void;
  onStatusMessage: (message: string | null, busy: boolean) => void;
  onCasesChange?: (cases: readonly VerificationCaseState[]) => void;
  onShowWitness?: (caseId: string) => void;
  portfolios?: PortfolioInfo[];
}

export interface VerificationPanelHandle {
  revalidate: (caseId: string) => void;
}

const pillSx = {
  fontSize: FONT_SIZE.xs,
  fontWeight: 500,
  color: 'text.secondary',
  bgcolor: 'var(--surface-panel-bg)',
  border: '1px solid var(--surface-border)',
  borderRadius: 999,
  px: 0.75,
  py: 0.05,
  whiteSpace: 'nowrap',
} as const;

function MetricsPill({
  source,
}: {
  source: MetricsPillSource;
}): React.JSX.Element {
  const tooltip = buildMetricsTooltip(source.metrics, {
    portfolioLabel: source.portfolioLabel,
    backendId: source.backendId,
  });
  const label = `${source.verb} in ${formatIsoDurationDetailed(source.totalDuration)}`;
  return (
    <Tooltip title={tooltip}>
      <Box component="span" sx={pillSx}>
        {label}
      </Box>
    </Tooltip>
  );
}

function deriveStatusMessage(state: VerificationState): string | null {
  if (state.phase === 'running') {
    const runningCase = state.cases.find((c) => c.status === 'running');
    if (runningCase) {
      const progress = state.progress ? ` - ${state.progress}` : '';
      return `Verifying ${runningCase.caseInfo.label}${progress}`;
    }
    return state.message ?? 'Running verification...';
  }
  if (state.phase === 'done' || state.phase === 'cancelled' || state.phase === 'error') {
    return state.message ?? null;
  }
  return null;
}

function VerificationPanelInner(
  {
    editorHandle,
    verificationCommand,
    discoveryCommand,
    validateWitnessCommand,
    connected,
    portfolioId,
    validationPortfolioId,
    autoValidate,
    onAutoValidateChange,
    panelHeight,
    onClose,
    onStatusMessage,
    onCasesChange,
    onShowWitness,
    portfolios,
  }: Props,
  ref: React.Ref<VerificationPanelHandle>,
): React.JSX.Element {
  const [verifyState, setVerifyState] = useState<VerificationState>({ phase: 'idle', cases: [] });
  const verifyStateRef = useRef(verifyState);
  const runHandleRef = useRef<RunVerificationHandle | null>(null);
  const discoveryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const stateUpdater = useCallback((next: VerificationState | ((prev: VerificationState) => VerificationState)): void => {
    setVerifyState((prev) => {
      const resolved = typeof next === 'function' ? next(prev) : next;
      verifyStateRef.current = resolved;
      return resolved;
    });
  }, []);

  // Report status message + busy upstream on every state transition.
  useEffect(() => {
    onStatusMessage(deriveStatusMessage(verifyState), verifyState.phase === 'running');
  }, [verifyState, onStatusMessage]);

  // Mirror cases out to the parent so it can drive the witness pane.
  useEffect(() => {
    onCasesChange?.(verifyState.cases);
  }, [verifyState.cases, onCasesChange]);

  const handleRefreshCases = useCallback((): void => {
    const client = editorHandle?.getLspClient();
    if (!client || !editorHandle) return;
    void discoverVerificationCases(client, discoveryCommand, editorHandle.getFileUri())
      .then((discoveredCases) => {
        stateUpdater((prev) => {
          if (prev.phase === 'running') return prev;

          const existingById = new Map(prev.cases.map((cs) => [cs.caseInfo.id, cs]));
          const mergedCases = discoveredCases.map((discovered) => {
            const existing = existingById.get(discovered.id);
            if (existing) {
              return { ...existing, caseInfo: discovered };
            }
            return { caseInfo: discovered, status: 'stale' as const };
          });

          return { ...prev, cases: mergedCases };
        });
      })
      .catch(() => { /* non-fatal */ });
  }, [editorHandle, discoveryCommand, stateUpdater]);

  // Discovery: run on connect, debounce on text changes
  useEffect(() => {
    if (!connected || !editorHandle) return;

    const scheduleDiscovery = (): void => {
      if (discoveryTimerRef.current) clearTimeout(discoveryTimerRef.current);
      discoveryTimerRef.current = setTimeout(() => {
        discoveryTimerRef.current = null;
        handleRefreshCases();
      }, 500);
    };

    handleRefreshCases();
    const removeContentListener = editorHandle.onEditorContentChange(scheduleDiscovery);

    return () => {
      if (discoveryTimerRef.current) {
        clearTimeout(discoveryTimerRef.current);
        discoveryTimerRef.current = null;
      }
      removeContentListener?.();
    };
  }, [connected, editorHandle, handleRefreshCases]);

  // Mirrored into a ref so the runner's getAutoValidateRequest sees the latest values when the
  // user toggles auto-validate or changes the validation portfolio mid-batch.
  const autoValidateOptionsRef = useRef({ autoValidate, validateWitnessCommand, validationPortfolioId });
  useEffect(() => {
    autoValidateOptionsRef.current = { autoValidate, validateWitnessCommand, validationPortfolioId };
  }, [autoValidate, validateWitnessCommand, validationPortfolioId]);

  const getAutoValidateRequest = useCallback(() => {
    const { autoValidate: live, validateWitnessCommand: cmd, validationPortfolioId: pid } = autoValidateOptionsRef.current;
    if (!live || !cmd) return null;
    return { command: cmd, ...(pid ? { portfolioId: pid } : {}) };
  }, []);

  const handleVerifyAll = useCallback(() => {
    if (verifyStateRef.current.phase === 'running') return;
    const client = editorHandle?.getLspClient();
    if (!client || !editorHandle) return;

    const cases = verifyStateRef.current.cases.map((cs) => cs.caseInfo);
    if (cases.length === 0) {
      stateUpdater({ phase: 'error', message: 'No verification cases found.', cases: [] });
      return;
    }

    runHandleRef.current = runAllVerifications(
      client,
      verificationCommand,
      editorHandle.getFileUri(),
      cases,
      stateUpdater,
      (listener) => editorHandle.addProgressListener(listener),
      { portfolioId, getAutoValidateRequest },
    );
  }, [editorHandle, verificationCommand, stateUpdater, portfolioId, getAutoValidateRequest]);

  const handleVerifySingle = useCallback((caseId: string) => {
    if (verifyStateRef.current.phase === 'running') return;
    const client = editorHandle?.getLspClient();
    if (!client || !editorHandle) return;

    const targetCase = verifyStateRef.current.cases.find((cs) => cs.caseInfo.id === caseId)?.caseInfo;
    if (!targetCase) return;

    // Stash the run handle so the panel-level Cancel button reaches both the batch and the
    // single-case flow uniformly.
    runHandleRef.current = verifySingleCase(
      client,
      verificationCommand,
      editorHandle.getFileUri(),
      targetCase,
      stateUpdater,
      (listener) => editorHandle.addProgressListener(listener),
      { portfolioId, getAutoValidateRequest },
    );
  }, [editorHandle, verificationCommand, stateUpdater, portfolioId, getAutoValidateRequest]);

  const handleCancel = useCallback(() => {
    runHandleRef.current?.cancel();
    runHandleRef.current = null;
  }, []);

  const handleCaseClick = useCallback((location: VerificationCaseLocation) => {
    editorHandle?.goToCase(location);
  }, [editorHandle]);

  useImperativeHandle(ref, () => ({
    revalidate: (caseId: string) => {
      if (!validateWitnessCommand) return;
      const client = editorHandle?.getLspClient();
      if (!client) return;
      const target = verifyStateRef.current.cases.find((cs) => cs.caseInfo.id === caseId);
      const witnessUri = target?.trace?.witnessUri;
      if (!witnessUri) return;
      dispatchAutoValidation(client, validateWitnessCommand, caseId, witnessUri, stateUpdater, {
        validationPortfolioId,
        caseLabel: target.caseInfo.label,
      });
    },
  }), [editorHandle, validateWitnessCommand, stateUpdater, validationPortfolioId]);

  const { phase, cases } = verifyState;
  const verifyBusy = phase === 'running';
  const stale = !connected;

  return (
    <Box
      sx={{
        height: `${panelHeight}px`,
        display: 'flex',
        flexDirection: 'column',
        bgcolor: 'var(--surface-panel-bg)',
        borderTop: '1px solid var(--surface-border)',
        opacity: stale ? 0.5 : 1,
        transition: 'opacity 0.2s',
        overflow: 'hidden',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          rowGap: 0.5,
          px: { xs: 1, sm: 1.5 },
          py: 0.25,
          gap: 0.5,
          flex: '0 0 auto',
        }}
      >
        <Typography
          variant="body2"
          sx={{
            fontWeight: 600,
            fontSize: FONT_SIZE.md,
          }}
        >
          Verification Cases
        </Typography>
        <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.5 }} />
        <SummaryCounts cases={cases} />
        <SummaryStatusIcon cases={cases} phase={phase} />
        <ProblemsPill editorHandle={editorHandle} />
        <Box sx={{ flex: 1 }} />

        {validateWitnessCommand && onAutoValidateChange && (
          <Tooltip title="Automatically validate the returned witness using a separate portfolio.">
            <FormControlLabel
              control={
                <Switch
                  size="small"
                  checked={autoValidate}
                  onChange={(_, checked) => onAutoValidateChange(checked)}
                  onClick={(event) => event.stopPropagation()}
                />
              }
              label={
                <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>auto-validate</Typography>
              }
              sx={{ mr: 0.5, ml: 0, '& .MuiFormControlLabel-label': { ml: 0.25 } }}
            />
          </Tooltip>
        )}
        <VerifyButton
          busy={verifyBusy}
          disabled={!connected}
          onVerify={(event) => { event.stopPropagation(); handleVerifyAll(); }}
          onCancel={(event) => { event.stopPropagation(); handleCancel(); }}
        />
        <RefreshButton
          disabled={!connected || verifyBusy}
          onClick={(event) => { event.stopPropagation(); handleRefreshCases(); }}
        />
        <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.5 }} />
        <Tooltip title="Close panel">
          <IconButton
            size="small"
            onClick={onClose}
            aria-label="Close verification panel"
            sx={{ color: 'text.secondary' }}
          >
            <CloseIcon sx={{ fontSize: ICON_SIZE.md }} />
          </IconButton>
        </Tooltip>
      </Box>

      <Box
        sx={{
          flex: '1 1 auto',
          minHeight: 0,
          overflowY: 'auto',
        }}
      >
          {phase === 'error' && verifyState.message && (
            <Box sx={{ px: 1.5, py: 0.75 }}>
              <Typography variant="body2" sx={{ fontSize: FONT_SIZE.md, color: 'var(--danger)' }}>
                {verifyState.message}
              </Typography>
            </Box>
          )}

          {cases.length > 0 && (
            <List dense disablePadding>
              {cases.map((caseState) => (
                <ListItemButton
                  key={caseState.caseInfo.id}
                  onClick={() => handleCaseClick(caseState.caseInfo.location)}
                  sx={{ py: 0.25, px: 1.5 }}
                >
                  <ListItemIcon sx={{ minWidth: 32 }}>
                    <CaseStatusIcon status={caseState.status} />
                  </ListItemIcon>
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', rowGap: 0.25, columnGap: 1, minWidth: 0 }}>
                        <Typography component="span" sx={{ fontSize: FONT_SIZE.md, color: stale ? 'var(--text-muted)' : 'var(--text)', wordBreak: 'break-word' }}>
                          {caseState.caseInfo.label}
                        </Typography>
                        {caseState.metrics !== undefined && isMeaningfulDuration(caseState.metrics.totalDuration) && (
                          <Box sx={{ display: { xs: 'none', sm: 'inline-flex' } }}>
                            <MetricsPill
                              source={{
                                verb: 'verified',
                                totalDuration: caseState.metrics.totalDuration,
                                metrics: caseState.metrics,
                                portfolioLabel: findPortfolioLabel(
                                  portfolios ?? [],
                                  caseState.portfolioId ?? caseState.backendId,
                                ),
                                backendId: caseState.backendId,
                              }}
                            />
                          </Box>
                        )}
                        {caseState.validationMetrics !== undefined && isMeaningfulDuration(caseState.validationMetrics.totalDuration) && (
                          <Box sx={{ display: { xs: 'none', sm: 'inline-flex' } }}>
                            <MetricsPill
                              source={{
                                verb: 'validated',
                                totalDuration: caseState.validationMetrics.totalDuration,
                                metrics: caseState.validationMetrics,
                                portfolioLabel: findPortfolioLabel(
                                  portfolios ?? [],
                                  caseState.validationPortfolioIdUsed ?? caseState.validationBackendId,
                                ),
                                backendId: caseState.validationBackendId,
                              }}
                            />
                          </Box>
                        )}
                        {caseState.validating && (
                          <Tooltip title="Validating witness in the background">
                            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: 'text.secondary', fontSize: FONT_SIZE.xs }}>
                              <CircularProgress size={10} thickness={6} sx={{ color: 'text.secondary' }} />
                              <span>validating...</span>
                            </Box>
                          </Tooltip>
                        )}
                      </Box>
                    }
                    secondary={
                      caseState.message != null && (caseState.status === 'failed' || caseState.status === 'errored' || caseState.status === 'inconclusive' || caseState.status === 'not_supported')
                        ? caseState.message
                        : null
                    }
                    slotProps={{
                      secondary: {
                        sx: {
                          fontSize: FONT_SIZE.sm,
                          color: caseState.status === 'failed' || caseState.status === 'errored' ? 'var(--danger)' : 'var(--text-muted)',
                        },
                      },
                    }}
                  />
                  {caseState.trace !== undefined && onShowWitness && (() => {
                    const descriptor = witnessIconDescriptor(caseState.witnessValidation);
                    return (
                      <Tooltip title={descriptor.tooltip}>
                        <IconButton
                          size="small"
                          onClick={(event) => { event.stopPropagation(); onShowWitness(caseState.caseInfo.id); }}
                          aria-label={descriptor.ariaLabel}
                          sx={{ color: descriptor.iconColor, ml: 0.5 }}
                        >
                          <Badge
                            color={descriptor.badgeColor}
                            variant="dot"
                            invisible={!descriptor.badgeVisible}
                            overlap="circular"
                          >
                            <ArticleOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />
                          </Badge>
                        </IconButton>
                      </Tooltip>
                    );
                  })()}
                  <Tooltip title="Go to definition">
                    <IconButton
                      size="small"
                      onClick={(event) => { event.stopPropagation(); handleCaseClick(caseState.caseInfo.location); }}
                      aria-label={`Go to definition of ${caseState.caseInfo.label}`}
                      sx={{ color: 'text.secondary', ml: 0.5, display: { xs: 'none', sm: 'inline-flex' } }}
                    >
                      <MyLocationOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Verify case">
                    <span>
                      <IconButton
                        size="small"
                        onClick={(event) => { event.stopPropagation(); handleVerifySingle(caseState.caseInfo.id); }}
                        disabled={!connected || verifyBusy}
                        aria-label={`Verify ${caseState.caseInfo.label}`}
                        sx={{ color: 'var(--accent)' }}
                      >
                        <PlayArrowIcon sx={{ fontSize: ICON_SIZE.lg }} />
                      </IconButton>
                    </span>
                  </Tooltip>
                </ListItemButton>
              ))}
            </List>
          )}

          {cases.length === 0 && connected && (
            <Box sx={{ px: 1.5, py: 1 }}>
              <Typography variant="body2" sx={{ fontSize: FONT_SIZE.md, color: 'text.secondary' }}>
                Discovering cases...
              </Typography>
            </Box>
          )}
      </Box>
    </Box>
  );
}

const VerificationPanel = forwardRef<VerificationPanelHandle, Props>(VerificationPanelInner);
VerificationPanel.displayName = 'VerificationPanel';
export default VerificationPanel;
