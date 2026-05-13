/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import CloseIcon from '@mui/icons-material/Close';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import type { LiveEditorHandle, LiveEditorStatus } from '../editor/LiveEditor';
import type { LspMetrics } from '../../lib/session/lspMetrics';
import { createApi, type InfoResponse, type SessionInfo } from '../../lib/api';
import { formatDuration, formatIsoDuration } from '../../lib/util/duration';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  open: boolean;
  onClose: () => void;
  connectionStatus: LiveEditorStatus;
  language: string;
  connectedSince: number | null;
  reconnectCount: number;
  editorHandle: LiveEditorHandle | null;
  backendUrl: string;
}


function formatElapsedMs(ms: number): string {
  return formatDuration(Math.floor(ms / 1000));
}

function formatLatencyMs(ms: number | null): string {
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

const cellSx = { py: 0.3, px: 1, border: 'none', fontSize: FONT_SIZE.sm, color: 'text.primary' } as const;
const labelSx = { ...cellSx, color: 'text.secondary', width: 120, whiteSpace: 'nowrap' } as const;
const monoSx = { ...cellSx, fontFamily: 'var(--font-mono)', fontSize: FONT_SIZE.xs } as const;
const sectionSx = {
  py: 0.5, px: 1, border: 'none', fontSize: FONT_SIZE.xs, fontWeight: 700,
  color: 'var(--accent)', textTransform: 'uppercase', letterSpacing: '0.05em',
  borderBottom: '1px solid var(--surface-border-soft)',
} as const;

function SectionHeader({ title }: { title: string }): React.JSX.Element {
  return (
    <TableRow>
      <TableCell colSpan={2} sx={sectionSx}>{title}</TableCell>
    </TableRow>
  );
}

function InfoRow({ label, value, mono }: { label: string; value: string; mono?: boolean | undefined }): React.JSX.Element {
  return (
    <TableRow sx={{ '&:hover': { bgcolor: 'var(--surface-border-soft)' } }}>
      <TableCell sx={labelSx}>{label}</TableCell>
      <TableCell sx={mono ? monoSx : cellSx}>{value}</TableCell>
    </TableRow>
  );
}

export default function DevInfoPanel({
  open,
  onClose,
  connectionStatus,
  language,
  connectedSince,
  reconnectCount,
  editorHandle,
  backendUrl,
}: Props): React.JSX.Element {
  const [sessionUptime, setSessionUptime] = useState<string>('-');
  const [pageUptime, setPageUptime] = useState<string>('0s');
  const [lspMetrics, setLspMetrics] = useState<LspMetrics | null>(null);
  const [backendInfo, setBackendInfo] = useState<InfoResponse | null>(null);
  const [sessionInfo, setSessionInfo] = useState<SessionInfo | null>(null);
  const [backendInfoError, setBackendInfoError] = useState(false);

  useEffect(() => {
    if (!open) return;
    const update = (): void => {
      setPageUptime(formatElapsedMs(Date.now() - PAGE_START));
      setSessionUptime(connectedSince !== null ? formatElapsedMs(Date.now() - connectedSince) : '-');
      setLspMetrics(editorHandle?.getLspMetrics() ?? null);

      const api = editorHandle?.getApi() ?? null;
      if (api) {
        api.getSessionInfo()
          .then((info) => { setSessionInfo(info); })
          .catch(() => { setSessionInfo(null); });
      } else {
        setSessionInfo(null);
      }
    };
    update();
    const interval = setInterval(update, 2000);
    return () => { clearInterval(interval); };
  }, [open, connectedSince, editorHandle]);

  useEffect(() => {
    if (!open) return;
    setBackendInfoError(false);
    const apiClient = createApi(backendUrl);
    let cancelled = false;
    apiClient.fetchInfo()
      .then((info) => {
        if (cancelled) return;
        setBackendInfo(info);
      })
      .catch(() => {
        if (cancelled) return;
        setBackendInfo(null);
        setBackendInfoError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [open, backendUrl]);

  const browser = getBrowserName();

  const [position, setPosition] = useState({ x: 0, y: 0 });
  const dragging = useRef(false);
  const dragOffset = useRef({ x: 0, y: 0 });

  const handleDragStart = useCallback((e: React.MouseEvent) => {
    dragging.current = true;
    dragOffset.current = { x: e.clientX - position.x, y: e.clientY - position.y };

    const handleMove = (ev: MouseEvent): void => {
      if (!dragging.current) return;
      setPosition({ x: ev.clientX - dragOffset.current.x, y: ev.clientY - dragOffset.current.y });
    };
    const handleUp = (): void => {
      dragging.current = false;
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
    };
    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);
  }, [position]);

  if (!open) return <></>;

  return (
    <Paper
      elevation={8}
      sx={{
        position: 'fixed',
        left: position.x,
        top: position.y,
        zIndex: 1300,
        bgcolor: 'var(--surface-bg)',
        color: 'text.primary',
        border: '1px solid var(--surface-border)',
        borderRadius: 2,
        minWidth: 380,
        maxHeight: '80vh',
        overflowY: 'auto',
      }}
    >
      <Box sx={{ px: 0.5, py: 0.5 }}>
        <Box
          onMouseDown={handleDragStart}
          sx={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 0.5, pb: 0.25,
            cursor: 'grab', userSelect: 'none', '&:active': { cursor: 'grabbing' },
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <DragIndicatorIcon sx={{ fontSize: ICON_SIZE.sm, color: 'text.secondary' }} />
            <Box sx={{ fontSize: FONT_SIZE.sm, fontWeight: 700, color: 'text.primary' }}>Developer Info</Box>
          </Box>
          <IconButton size="small" onClick={onClose} sx={{ color: 'text.secondary' }}>
            <CloseIcon sx={{ fontSize: ICON_SIZE.sm }} />
          </IconButton>
        </Box>
        <Table size="small" sx={{ tableLayout: 'auto' }}>
          <TableBody>
            <SectionHeader title="Frontend" />
            <InfoRow label="Language" value={language} />
            <InfoRow label="Commit" value={__GIT_COMMIT__} mono />
            <InfoRow label="Built" value={__BUILD_TIME__} />
            <InfoRow label="Browser" value={browser} />
            <InfoRow label="Page uptime" value={pageUptime} />

            <SectionHeader title="Backend" />
            {backendInfoError ? (
              <InfoRow label="Status" value="unreachable" />
            ) : !backendInfo ? (
              <InfoRow label="Status" value="loading..." />
            ) : (
              <>
                <InfoRow label="Commit" value={backendInfo.commit} mono />
                <InfoRow label="Built" value={backendInfo.buildTime} />
                <InfoRow label="Uptime" value={formatIsoDuration(backendInfo.uptime)} />
                <InfoRow label="Sessions" value={`${backendInfo.activeSessions} / ${backendInfo.maxSessions}`} />
              </>
            )}

            <SectionHeader title="LSP Connection" />
            <InfoRow label="Status" value={connectionStatus} />
            <InfoRow label="Uptime" value={sessionUptime} />
            <InfoRow label="Reconnects" value={String(reconnectCount)} />
            <InfoRow label="Requests" value={lspMetrics ? String(lspMetrics.requestCount) : '-'} />
            <InfoRow label="Notifications" value={lspMetrics ? String(lspMetrics.notificationCount) : '-'} />
            <InfoRow label="Errors" value={lspMetrics ? String(lspMetrics.errorCount) : '-'} />
            <InfoRow label="Last response" value={formatLatencyMs(lspMetrics?.lastResponseTimeMs ?? null)} mono />
            <InfoRow label="Avg response" value={formatLatencyMs(lspMetrics?.avgResponseTimeMs ?? null)} mono />

            <SectionHeader title="Session" />
            <InfoRow label="Status" value={sessionInfo ? 'connected' : connectionStatus === 'reconnecting' ? 'reconnecting' : 'disconnected'} />
            <InfoRow label="Session ID" value={sessionInfo?.sessionId ?? '-'} mono />
            <InfoRow label="Uptime" value={sessionInfo ? formatIsoDuration(sessionInfo.uptime) : '-'} />
            <InfoRow label="Last activity (in / out)" value={sessionInfo ? `${formatIsoDuration(sessionInfo.sessionLspInfo.timeSinceLastClientMessage)} / ${formatIsoDuration(sessionInfo.sessionLspInfo.timeSinceLastServerMessage)} ago` : '-'} />
            <InfoRow label="Verifications" value={sessionInfo ? String(sessionInfo.activeVerifications.length) : '-'} />
          </TableBody>
        </Table>
      </Box>
    </Paper>
  );
}
