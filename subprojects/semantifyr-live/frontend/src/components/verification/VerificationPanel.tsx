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
import Tooltip from '@mui/material/Tooltip';
import Divider from '@mui/material/Divider';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import MyLocationOutlinedIcon from '@mui/icons-material/MyLocationOutlined';

import {
  discoverVerificationCases,
  runAllVerifications,
  verifySingleCase,
  type VerificationCaseLocation,
  type VerificationState,
  type RunVerificationHandle,
} from '../../lib/verification';
import type { LiveEditorHandle } from '../LiveEditor';
import VerifyButton from './VerifyButton';
import RefreshButton from './RefreshButton';
import { CaseStatusIcon, SummaryCounts, SummaryStatusIcon } from './StatusDisplay';

interface Props {
  editorHandle: LiveEditorHandle | null;
  verificationCommand: string;
  discoveryCommand: string;
  connected: boolean;
  onStatusMessage: (message: string | null) => void;
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
  verificationCommand,
  discoveryCommand,
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
    );
  }, [editorHandle, verificationCommand, stateUpdater]);

  const handleVerifySingle = useCallback((caseId: string) => {
    if (verifyStateRef.current.phase === 'running') return;
    const client = editorHandle?.getLspClient();
    if (!client || !editorHandle) return;

    const targetCase = verifyStateRef.current.cases.find((cs) => cs.caseInfo.id === caseId)?.caseInfo;
    if (!targetCase) return;

    void verifySingleCase(client, verificationCommand, editorHandle.getFileUri(), targetCase, stateUpdater);
  }, [editorHandle, verificationCommand, stateUpdater]);

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
