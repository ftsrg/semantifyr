/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Popover from '@mui/material/Popover';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import CircularProgress from '@mui/material/CircularProgress';
import type { LiveEditorHandle, LiveEditorStatus, LspMetrics } from './LiveEditor';

interface Props {
  anchorEl: HTMLElement | null;
  open: boolean;
  onClose: () => void;
  connectionStatus: LiveEditorStatus;
  language: string;
  connectedSince: number | null;
  reconnectCount: number;
  editorHandle: LiveEditorHandle | null;
}

interface BackendHealth {
  status: string;
  activeSessions: number;
  maxSessions: number;
  uptimeSeconds?: number | undefined;
}

function formatDuration(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

function formatMs(ms: number | null): string {
  if (ms === null) return '-';
  return `${ms}ms`;
}

function getBrowserName(): string {
  const ua = navigator.userAgent;
  if (ua.includes('Firefox/')) {
    const match = /Firefox\/(\d+)/.exec(ua);
    return `Firefox ${match?.[1] ?? ''}`;
  }
  if (ua.includes('Edg/')) {
    const match = /Edg\/(\d+)/.exec(ua);
    return `Edge ${match?.[1] ?? ''}`;
  }
  if (ua.includes('Chrome/')) {
    const match = /Chrome\/(\d+)/.exec(ua);
    return `Chrome ${match?.[1] ?? ''}`;
  }
  if (ua.includes('Safari/') && !ua.includes('Chrome')) {
    const match = /Version\/(\d+)/.exec(ua);
    return `Safari ${match?.[1] ?? ''}`;
  }
  return 'Unknown';
}

const PAGE_START = Date.now();

const cellSx = { py: 0.5, px: 1.5, border: 'none', fontSize: '0.78rem', color: 'var(--text)' } as const;
const labelSx = { ...cellSx, color: 'var(--text-muted)' } as const;
const monoSx = { ...cellSx, fontFamily: 'var(--font-mono)' } as const;

function InfoRow({ label, value, mono }: { label: string; value: string; mono?: boolean | undefined }): React.JSX.Element {
  return (
    <TableRow>
      <TableCell sx={labelSx}>{label}</TableCell>
      <TableCell sx={mono ? monoSx : cellSx}>{value}</TableCell>
    </TableRow>
  );
}

export default function DevInfoPanel({
  anchorEl,
  open,
  onClose,
  connectionStatus,
  language,
  connectedSince,
  reconnectCount,
  editorHandle,
}: Props): React.JSX.Element {
  const [sessionUptime, setSessionUptime] = useState<string>('-');
  const [pageUptime, setPageUptime] = useState<string>('0s');
  const [lspMetrics, setLspMetrics] = useState<LspMetrics | null>(null);
  const [backendHealth, setBackendHealth] = useState<BackendHealth | null>(null);
  const [healthError, setHealthError] = useState(false);

  // Live-update uptimes and metrics
  useEffect(() => {
    if (!open) return;
    const update = (): void => {
      setPageUptime(formatDuration(Date.now() - PAGE_START));
      setSessionUptime(connectedSince !== null ? formatDuration(Date.now() - connectedSince) : '-');
      setLspMetrics(editorHandle?.getLspMetrics() ?? null);
    };
    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, [open, connectedSince, editorHandle]);

  // Fetch backend health when panel opens
  useEffect(() => {
    if (!open) return;
    setHealthError(false);
    fetch('/api/health')
      .then((res) => {
        if (!res.ok) throw new Error('Failed');
        return res.json() as Promise<BackendHealth>;
      })
      .then(setBackendHealth)
      .catch(() => {
        setBackendHealth(null);
        setHealthError(true);
      });
  }, [open]);

  const browser = getBrowserName();

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      transformOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      slotProps={{
        paper: {
          sx: {
            bgcolor: 'var(--surface-bg)',
            color: 'var(--text)',
            border: '1px solid var(--surface-border)',
            minWidth: 300,
          },
        },
      }}
    >
      <Box sx={{ px: 1.5, py: 1 }}>
        <Typography variant="subtitle2" sx={{ fontSize: '0.82rem', fontWeight: 700, mb: 0.5 }}>
          Frontend
        </Typography>
        <Table size="small">
          <TableBody>
            <InfoRow label="Language" value={language} />
            <InfoRow label="Commit" value={__GIT_COMMIT__} mono />
            <InfoRow label="Built" value={__BUILD_TIME__} />
            <InfoRow label="Browser" value={browser} />
            <InfoRow label="Page uptime" value={pageUptime} />
          </TableBody>
        </Table>

        <Typography variant="subtitle2" sx={{ fontSize: '0.82rem', fontWeight: 700, mt: 1.5, mb: 0.5 }}>
          LSP Connection
        </Typography>
        <Table size="small">
          <TableBody>
            <InfoRow label="Status" value={connectionStatus} />
            <InfoRow label="Session uptime" value={sessionUptime} />
            <InfoRow label="Reconnects" value={String(reconnectCount)} />
            <InfoRow label="Requests" value={lspMetrics ? String(lspMetrics.requestCount) : '-'} />
            <InfoRow label="Notifications" value={lspMetrics ? String(lspMetrics.notificationCount) : '-'} />
            <InfoRow label="Errors" value={lspMetrics ? String(lspMetrics.errorCount) : '-'} />
            <InfoRow label="Last response" value={formatMs(lspMetrics?.lastResponseTimeMs ?? null)} mono />
            <InfoRow label="Avg response" value={formatMs(lspMetrics?.avgResponseTimeMs ?? null)} mono />
          </TableBody>
        </Table>

        <Typography variant="subtitle2" sx={{ fontSize: '0.82rem', fontWeight: 700, mt: 1.5, mb: 0.5 }}>
          Backend
        </Typography>
        {healthError && (
          <Typography variant="body2" sx={{ fontSize: '0.78rem', color: 'var(--danger)', px: 1.5, pb: 0.5 }}>
            Unable to reach backend
          </Typography>
        )}
        {!healthError && !backendHealth && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
            <CircularProgress size={16} sx={{ color: 'var(--text-muted)' }} />
          </Box>
        )}
        {backendHealth && (
          <Table size="small">
            <TableBody>
              <InfoRow label="Status" value={backendHealth.status} />
              <InfoRow label="Sessions" value={`${backendHealth.activeSessions} / ${backendHealth.maxSessions}`} />
              {backendHealth.uptimeSeconds != null && (
                <InfoRow label="Uptime" value={formatDuration(backendHealth.uptimeSeconds * 1000)} />
              )}
            </TableBody>
          </Table>
        )}
      </Box>
    </Popover>
  );
}
