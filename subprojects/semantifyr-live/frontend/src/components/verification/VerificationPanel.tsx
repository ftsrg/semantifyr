/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Collapse from '@mui/material/Collapse';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import CircularProgress from '@mui/material/CircularProgress';
import Tooltip from '@mui/material/Tooltip';
import Divider from '@mui/material/Divider';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import UpdateIcon from '@mui/icons-material/Update';
import RadioButtonUncheckedOutlinedIcon from '@mui/icons-material/RadioButtonUncheckedOutlined';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import ErrorOutlinedIcon from '@mui/icons-material/ErrorOutlined';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import MyLocationOutlinedIcon from '@mui/icons-material/MyLocationOutlined';


import {
  discoverVerificationCases,
  runVerifyAll,
  verifySingleCase,
  type VerificationCaseLocation,
  type VerificationCaseStatus,
  type VerificationCaseState,
  type VerificationState,
  type RunVerificationHandle,
} from '../../lib/verification';
import type { LiveEditorHandle } from '../LiveEditor';
import VerifyButton from './VerifyButton';
import RefreshButton from './RefreshButton';

interface Props {
  editorHandle: LiveEditorHandle | null;
  verifyCommand: string;
  connected: boolean;
  onStatusMessage: (message: string | null) => void;
}

const ICON_SIZE = 20;
const iconBoxSx = { width: ICON_SIZE, height: ICON_SIZE, display: 'flex', alignItems: 'center', justifyContent: 'center' } as const;

type StatusCategory = 'passed' | 'failed' | 'errored' | 'unknown';

function statusColorVar(category: StatusCategory): string {
  switch (category) {
    case 'passed': return 'var(--success)';
    case 'failed': return 'var(--danger)';
    case 'errored': return 'var(--danger)';
    case 'unknown': return 'var(--text-muted)';
  }
}

function StatusIcon({ category, size }: { category: StatusCategory; size: number }): React.JSX.Element {
  const color = statusColorVar(category);
  switch (category) {
    case 'passed':
      return <CheckCircleOutlinedIcon sx={{ fontSize: size, color }} />;
    case 'failed':
      return <CancelOutlinedIcon sx={{ fontSize: size, color }} />;
    case 'errored':
      return <ErrorOutlinedIcon sx={{ fontSize: size, color }} />;
    case 'unknown':
      return <RadioButtonUncheckedOutlinedIcon sx={{ fontSize: Math.round(size * 0.8), color }} />;
  }
}

const STATUS_TOOLTIPS: Record<VerificationCaseStatus, string> = {
  passed: 'Passed',
  failed: 'Failed',
  errored: 'Error during verification',
  running: 'Running',
  queued: 'Queued for verification',
  stale: 'Not yet verified',
};

function CaseStatusIcon({ status }: { status: VerificationCaseStatus }): React.JSX.Element {
  const tooltip = STATUS_TOOLTIPS[status];
  switch (status) {
    case 'passed':
      return <Tooltip title={tooltip}><Box sx={iconBoxSx}><StatusIcon category="passed" size={ICON_SIZE} /></Box></Tooltip>;
    case 'failed':
      return <Tooltip title={tooltip}><Box sx={iconBoxSx}><StatusIcon category="failed" size={ICON_SIZE} /></Box></Tooltip>;
    case 'errored':
      return <Tooltip title={tooltip}><Box sx={iconBoxSx}><StatusIcon category="errored" size={ICON_SIZE} /></Box></Tooltip>;
    case 'running':
      return <Tooltip title={tooltip}><Box sx={iconBoxSx}><CircularProgress size={16} sx={{ color: 'var(--warning)' }} /></Box></Tooltip>;
    case 'queued':
      return <Tooltip title={tooltip}><Box sx={iconBoxSx}><UpdateIcon sx={{ fontSize: ICON_SIZE, color: 'var(--warning)' }} /></Box></Tooltip>;
    case 'stale':
      return <Tooltip title={tooltip}><Box sx={iconBoxSx}><StatusIcon category="unknown" size={ICON_SIZE} /></Box></Tooltip>;
  }
}

interface CaseCounts {
  failed: number;
  errored: number;
  passed: number;
  unknown: number;
  total: number;
}

function countCases(cases: readonly VerificationCaseState[]): CaseCounts {
  let failed = 0;
  let errored = 0;
  let passed = 0;
  for (const c of cases) {
    if (c.status === 'passed') passed++;
    else if (c.status === 'failed') failed++;
    else if (c.status === 'errored') errored++;
  }
  return { failed, errored, passed, unknown: cases.length - failed - errored - passed, total: cases.length };
}

const COUNT_TOOLTIPS: Record<StatusCategory, string> = {
  passed: 'Passed cases',
  failed: 'Failed or errored cases',
  errored: 'Errored cases',
  unknown: 'Not yet verified',
};

function CountBadge({ category, count }: { category: StatusCategory; count: number }): React.JSX.Element {
  const color = statusColorVar(category);
  const muted = count === 0;
  return (
    <Tooltip title={`${count} ${COUNT_TOOLTIPS[category]}`}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.25, opacity: muted ? 0.4 : 1 }}>
        <StatusIcon category={category} size={12} />
        <Typography variant="caption" sx={{ fontSize: '0.78rem', color, fontWeight: 600 }}>{count}</Typography>
      </Box>
    </Tooltip>
  );
}

function SummaryCounts({ cases }: { cases: readonly VerificationCaseState[] }): React.JSX.Element {
  const { failed, errored, passed, unknown, total } = countCases(cases);
  if (total === 0) return <></>;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <CountBadge category="unknown" count={unknown} />
      <CountBadge category="failed" count={failed + errored} />

      <CountBadge category="passed" count={passed} />
      <Typography variant="caption" sx={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}> / {total}</Typography>
    </Box>
  );
}

function SummaryStatusIcon({ cases, phase }: { cases: readonly VerificationCaseState[]; phase: VerificationState['phase'] }): React.JSX.Element {
  if (phase === 'running') {
    return <Tooltip title="Verification in progress"><Box sx={iconBoxSx}><UpdateIcon sx={{ fontSize: ICON_SIZE, color: 'var(--warning)' }} /></Box></Tooltip>;
  }
  const { passed, failed, errored } = countCases(cases);
  if (passed === cases.length && cases.length > 0) {
    return <Tooltip title="All cases passed"><Box sx={iconBoxSx}><DoneAllIcon sx={{ fontSize: ICON_SIZE, color: 'var(--success)' }} /></Box></Tooltip>;
  }
  if (errored > 0 && failed === 0) {
    return <Tooltip title={`${errored} case${errored === 1 ? '' : 's'} errored`}><Box sx={iconBoxSx}><StatusIcon category="errored" size={ICON_SIZE} /></Box></Tooltip>;
  }
  if (failed > 0 || errored > 0) {
    const parts = [failed > 0 ? `${failed} failed` : '', errored > 0 ? `${errored} errored` : ''].filter(Boolean).join(', ');
    return <Tooltip title={parts}><Box sx={iconBoxSx}><StatusIcon category="failed" size={ICON_SIZE} /></Box></Tooltip>;
  }
  return <Tooltip title="No results yet"><Box sx={iconBoxSx}><StatusIcon category="unknown" size={ICON_SIZE} /></Box></Tooltip>;
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

export default function VerificationPanel({
  editorHandle,
  verifyCommand,
  connected,
  onStatusMessage,
}: Props): React.JSX.Element {
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

  const handleRefreshCases = useCallback((): void => {
    const client = editorHandle?.getLspClient();
    if (!client || !editorHandle) return;
    void discoverVerificationCases(client, editorHandle.getFileUri())
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
  }, [editorHandle, stateUpdater]);

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

  const handleVerifyAll = useCallback(() => {
    if (verifyStateRef.current.phase === 'running') return;
    const client = editorHandle?.getLspClient();
    if (!client || !editorHandle) return;

    const cases = verifyStateRef.current.cases.map((cs) => cs.caseInfo);
    if (cases.length === 0) {
      stateUpdater({ phase: 'error', message: 'No verification cases found.', cases: [] });
      return;
    }

    runHandleRef.current = runVerifyAll(
      client,
      verifyCommand,
      editorHandle.getFileUri(),
      cases,
      stateUpdater,
      (listener) => editorHandle.addProgressListener(listener),
    );
  }, [editorHandle, verifyCommand, stateUpdater]);

  const handleVerifySingle = useCallback((caseId: string) => {
    if (verifyStateRef.current.phase === 'running') return;
    const client = editorHandle?.getLspClient();
    if (!client || !editorHandle) return;

    const targetCase = verifyStateRef.current.cases.find((cs) => cs.caseInfo.id === caseId)?.caseInfo;
    if (!targetCase) return;

    void verifySingleCase(client, verifyCommand, editorHandle.getFileUri(), targetCase, stateUpdater);
  }, [editorHandle, verifyCommand, stateUpdater]);

  const handleCancel = useCallback(() => {
    runHandleRef.current?.cancel();
    runHandleRef.current = null;
  }, []);

  const handleCaseClick = useCallback((location: VerificationCaseLocation) => {
    editorHandle?.goToCase(location);
  }, [editorHandle]);

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
          px: 1.5,
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
        <Box sx={{ flex: 1 }} />

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
        <Box sx={{ maxHeight: 220, overflowY: 'auto' }}>
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
                    primary={caseState.caseInfo.label}
                    secondary={(caseState.status === 'failed' || caseState.status === 'errored') && caseState.message ? caseState.message : null}
                    slotProps={{
                      primary: { sx: { fontSize: '0.9rem', color: stale ? 'var(--text-muted)' : 'var(--text)' } },
                      secondary: { sx: { fontSize: '0.82rem', color: 'var(--danger)' } },
                    }}
                  />
                  <Tooltip title="Go to definition">
                    <IconButton
                      size="small"
                      onClick={(event) => { event.stopPropagation(); handleCaseClick(caseState.caseInfo.location); }}
                      sx={{ color: 'var(--text-muted)', ml: 0.5 }}
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
