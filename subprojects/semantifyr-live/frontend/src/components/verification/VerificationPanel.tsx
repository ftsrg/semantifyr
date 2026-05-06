/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Collapse from '@mui/material/Collapse';
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
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
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
import type { LiveEditorHandle } from '../LiveEditor';
import VerifyButton from './VerifyButton';
import RefreshButton from './RefreshButton';
import ProblemsPill from './ProblemsPill';
import { CaseStatusIcon, SummaryCounts, SummaryStatusIcon } from './StatusDisplay';
import { formatIsoDurationDetailed } from '../../lib/duration';
import { buildMetricsTooltip, isMeaningfulDuration } from '../../lib/metricsTooltip';

interface MetricsPillSource {
  /** Past-participle pill label, e.g. "verified". */
  verb: 'verified' | 'validated';
  totalDuration: string;
  metrics: VerificationCaseState['metrics'];
  portfolioLabel?: string | undefined;
  backendId?: string | undefined;
}

function findPortfolioLabel(
  portfolios: readonly { id: string; displayName: string }[],
  id: string | undefined,
): string | undefined {
  if (!id) {
    return undefined;
  }
  return portfolios.find((p) => p.id === id)?.displayName ?? id;
}

interface Props {
  editorHandle: LiveEditorHandle | null;
  verificationCommand: string;
  discoveryCommand: string;
  validateWitnessCommand?: string | undefined;
  connected: boolean;
  portfolioId: string;
  /** Portfolio id used by the auto-validation and the panel's revalidate handle. */
  validationPortfolioId: string;
  /** When false, verify finishes without firing an automatic follow-up validation. */
  autoValidate: boolean;
  /** Toggling auto-validate; rendered in the panel header next to the verify controls. */
  onAutoValidateChange?: (enabled: boolean) => void;
  /** Max pixel height for the case list body; overrides the responsive default. */
  caseListMaxHeight?: number | undefined;
  onStatusMessage: (message: string | null) => void;
  onCasesChange?: (cases: readonly VerificationCaseState[]) => void;
  onShowWitness?: (caseId: string) => void;
  /** Portfolio catalogue used to render display names in the pill tooltips. */
  portfolios?: readonly { id: string; displayName: string }[];
}

/** Imperative handle exposed to the parent so the witness pane can re-fire validation per case. */
export interface VerificationPanelHandle {
  /** Re-runs witness validation for the given case id; folds the outcome into that case's state. */
  revalidate: (caseId: string) => void;
}

function isFlaggedValidation(status: VerificationCaseState['witnessValidation']): boolean {
  // The Show-witness button is decorated with a red dot when the cross-validation portfolio
  // returned anything other than a clean "valid" - even errored counts; the witness pane
  // surfaces the detailed reason via ValidationChip.
  return status === 'invalid' || status === 'inconclusive' || status === 'errored';
}

const pillSx = {
  fontSize: '0.7rem',
  fontWeight: 500,
  color: 'var(--text-muted)',
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
    caseListMaxHeight,
    onStatusMessage,
    onCasesChange,
    onShowWitness,
    portfolios,
  }: Props,
  ref: React.Ref<VerificationPanelHandle>,
): React.JSX.Element {
  const [drawerOpen, setDrawerOpen] = useState(true);
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

  // Report status message upstream whenever verification state changes
  useEffect(() => {
    onStatusMessage(deriveStatusMessage(verifyState));
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
        bgcolor: 'var(--surface-panel-bg)',
        borderTop: '1px solid var(--surface-border)',
        opacity: stale ? 0.5 : 1,
        transition: 'opacity 0.2s',
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
        }}
      >
        <Typography
          variant="body2"
          sx={{
            fontWeight: 600,
            fontSize: '0.9rem',
            color: 'var(--text)',
          }}
        >
          Verification Cases
        </Typography>
        <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.5, borderColor: 'var(--surface-border)' }} />
        <SummaryCounts cases={cases} />
        <SummaryStatusIcon cases={cases} phase={phase} />
        <ProblemsPill editorHandle={editorHandle} />
        <Box sx={{ flex: 1 }} />

        {validateWitnessCommand && onAutoValidateChange && (
          <Tooltip title="When enabled, every verify success kicks off a witness cross-validation in the background.">
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
                <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>auto-validate</Typography>
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
        <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.5, borderColor: 'var(--surface-border)' }} />
        <Tooltip title={drawerOpen ? 'Collapse panel' : 'Expand panel'}>
          <IconButton size="small" onClick={() => setDrawerOpen((prev) => !prev)} sx={{ color: 'var(--text-muted)' }}>
            {drawerOpen ? <KeyboardArrowDownIcon sx={{ fontSize: 20 }} /> : <KeyboardArrowUpIcon sx={{ fontSize: 20 }} />}
          </IconButton>
        </Tooltip>
      </Box>

      <Collapse in={drawerOpen}>
        <Box
          sx={{
            maxHeight: caseListMaxHeight ?? { xs: 160, sm: 220 },
            overflowY: 'auto',
          }}
        >
          {phase === 'error' && verifyState.message && (
            <Box sx={{ px: 1.5, py: 0.75 }}>
              <Typography variant="body2" sx={{ fontSize: '0.85rem', color: 'var(--danger)' }}>
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
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography component="span" sx={{ fontSize: '0.9rem', color: stale ? 'var(--text-muted)' : 'var(--text)' }}>
                          {caseState.caseInfo.label}
                        </Typography>
                        {caseState.metrics !== undefined && isMeaningfulDuration(caseState.metrics.totalDuration) && (
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
                        )}
                        {caseState.validationMetrics !== undefined && isMeaningfulDuration(caseState.validationMetrics.totalDuration) && (
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
                        )}
                        {caseState.validating && (
                          <Tooltip title="Cross-validating witness in the background">
                            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: 'var(--text-muted)', fontSize: '0.7rem' }}>
                              <CircularProgress size={10} thickness={6} sx={{ color: 'var(--text-muted)' }} />
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
                          fontSize: '0.82rem',
                          color: caseState.status === 'failed' || caseState.status === 'errored' ? 'var(--danger)' : 'var(--text-muted)',
                        },
                      },
                    }}
                  />
                  {caseState.trace !== undefined && onShowWitness && (() => {
                    const flagged = isFlaggedValidation(caseState.witnessValidation);
                    return (
                      <Tooltip title={flagged ? 'Show witness (validation portfolio disagreed)' : 'Show witness'}>
                        <IconButton
                          size="small"
                          onClick={(event) => { event.stopPropagation(); onShowWitness(caseState.caseInfo.id); }}
                          sx={{ color: flagged ? 'var(--danger)' : 'var(--text-muted)', ml: 0.5 }}
                        >
                          <Badge
                            color="error"
                            variant="dot"
                            invisible={!flagged}
                            overlap="circular"
                          >
                            <ArticleOutlinedIcon sx={{ fontSize: 18 }} />
                          </Badge>
                        </IconButton>
                      </Tooltip>
                    );
                  })()}
                  <Tooltip title="Go to definition">
                    <IconButton
                      size="small"
                      onClick={(event) => { event.stopPropagation(); handleCaseClick(caseState.caseInfo.location); }}
                      sx={{ color: 'var(--text-muted)', ml: 0.5, display: { xs: 'none', sm: 'inline-flex' } }}
                    >
                      <MyLocationOutlinedIcon sx={{ fontSize: 18 }} />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Verify case">
                    <span>
                      <IconButton
                        size="small"
                        onClick={(event) => { event.stopPropagation(); handleVerifySingle(caseState.caseInfo.id); }}
                        disabled={!connected || verifyBusy}
                        sx={{ color: 'var(--accent)' }}
                      >
                        <PlayArrowIcon sx={{ fontSize: 20 }} />
                      </IconButton>
                    </span>
                  </Tooltip>
                </ListItemButton>
              ))}
            </List>
          )}

          {cases.length === 0 && connected && (
            <Box sx={{ px: 1.5, py: 1 }}>
              <Typography variant="body2" sx={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                Discovering cases...
              </Typography>
            </Box>
          )}
        </Box>
      </Collapse>
    </Box>
  );
}

const VerificationPanel = forwardRef<VerificationPanelHandle, Props>(VerificationPanelInner);
VerificationPanel.displayName = 'VerificationPanel';
export default VerificationPanel;
