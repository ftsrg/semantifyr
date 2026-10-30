/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useState } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import StopIcon from '@mui/icons-material/Stop';
import type { SessionInfo } from '../../lib/api';
import { formatIsoDuration } from '../../lib/util/duration';
import KindChip from '../verification/KindChip';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  sessions: readonly SessionInfo[];
  onCancelSession: (sessionId: string) => void;
  onCancelVerification: (verificationId: string) => void;
}

function shortenSessionId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

export default function SessionsCardList({
  sessions,
  onCancelSession,
  onCancelVerification,
}: Props): React.JSX.Element {
  if (sessions.length === 0) {
    return (
      <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)' }}>
        <Typography
          sx={{ p: 3, color: 'text.secondary', fontStyle: 'italic', fontSize: FONT_SIZE.md, textAlign: 'center' }}
        >
          No active sessions.
        </Typography>
      </Paper>
    );
  }
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {sessions.map((session) => (
        <SessionCard
          key={session.sessionId}
          session={session}
          onCancelSession={onCancelSession}
          onCancelVerification={onCancelVerification}
        />
      ))}
    </Box>
  );
}

interface SessionCardProps {
  session: SessionInfo;
  onCancelSession: (sessionId: string) => void;
  onCancelVerification: (verificationId: string) => void;
}

function SessionCard({ session, onCancelSession, onCancelVerification }: SessionCardProps): React.JSX.Element {
  const [open, setOpen] = useState(false);
  const running = session.activeVerifications.filter((v) => v.state === 'Running').length;
  const queued = session.activeVerifications.filter((v) => v.state === 'Queued').length;
  const inFlight = session.activeVerifications.length;
  return (
    <Paper
      sx={{
        bgcolor: 'var(--surface-bg)',
        border: '1px solid var(--surface-border)',
        overflow: 'hidden',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          p: 1,
          cursor: 'pointer',
          '&:hover': { bgcolor: 'var(--surface-panel-bg)' },
        }}
        onClick={() => { setOpen((prev) => !prev); }}
      >
        <IconButton size="small" sx={{ color: 'text.secondary', p: 0.25 }}>
          {open ? <KeyboardArrowDownIcon sx={{ fontSize: ICON_SIZE.md }} /> : <KeyboardArrowRightIcon sx={{ fontSize: ICON_SIZE.md }} />}
        </IconButton>
        <Chip
          label={session.flavorId}
          size="small"
          sx={{ bgcolor: 'var(--surface-panel-bg)', color: 'text.primary', fontWeight: 500, fontSize: FONT_SIZE.xs, height: 20 }}
        />
        <Tooltip title={session.sessionId}>
          <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>
            {shortenSessionId(session.sessionId)}
          </Typography>
        </Tooltip>
        <Box sx={{ flex: 1, minWidth: 0 }} />
        {inFlight > 0 ? (
          <Chip
            label={queued > 0 ? `${running} running, ${queued} queued` : `${running} running`}
            size="small"
            sx={{ bgcolor: 'var(--warning-soft-bg)', color: 'var(--warning)', fontWeight: 600, fontSize: FONT_SIZE.xs, height: 20 }}
          />
        ) : null}
        <Tooltip title="Kill session">
          <IconButton
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              onCancelSession(session.sessionId);
            }}
            sx={{ color: 'var(--danger)' }}
          >
            <StopIcon sx={{ fontSize: ICON_SIZE.md }} />
          </IconButton>
        </Tooltip>
      </Box>
      <Collapse in={open} timeout="auto" unmountOnExit>
        <Box
          sx={{
            bgcolor: 'var(--surface-panel-bg)',
            borderTop: '1px solid var(--surface-border)',
            px: 2,
            py: 1.25,
            display: 'flex',
            flexDirection: 'column',
            gap: 1,
          }}
        >
          <DetailRow label="Session id" value={session.sessionId} mono />
          <DetailRow label="Remote IP" value={session.remoteIp} />
          <DetailRow label="Uptime" value={formatIsoDuration(session.uptime)} />
          <DetailRow label="Working directory" value={session.workingDirectory} mono />
          {session.activeVerifications.length > 0 && (
            <Box>
              <Typography
                sx={{
                  fontSize: FONT_SIZE.xs,
                  color: 'text.secondary',
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em',
                  fontWeight: 600,
                  mt: 0.5,
                  mb: 0.25,
                }}
              >
                In-flight verifications
              </Typography>
              {session.activeVerifications.map((v) => (
                <Box
                  key={v.verificationId}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1,
                    py: 0.25,
                    fontFamily: 'var(--font-mono)',
                    fontSize: FONT_SIZE.sm,
                    flexWrap: 'wrap',
                  }}
                >
                  <KindChip kind={v.kind} />
                  <Chip
                    label={v.portfolioId}
                    size="small"
                    sx={{
                      bgcolor: 'var(--surface-bg)',
                      color: 'text.secondary',
                      fontWeight: 500,
                      fontSize: FONT_SIZE.xs,
                      height: 20,
                      fontFamily: 'inherit',
                    }}
                  />
                  <Tooltip title={v.verificationId}>
                    <Box component="span" sx={{ color: 'text.secondary' }}>
                      #{v.verificationId.slice(0, 8)}
                    </Box>
                  </Tooltip>
                  <Box component="span" sx={{ color: 'text.secondary', fontSize: FONT_SIZE.xs }}>
                    {formatIsoDuration(v.elapsed)}
                  </Box>
                  <Box sx={{ flex: 1 }} />
                  <Tooltip title="Cancel">
                    <IconButton size="small" onClick={() => { onCancelVerification(v.verificationId); }} sx={{ color: 'var(--danger)' }}>
                      <StopIcon sx={{ fontSize: ICON_SIZE.sm }} />
                    </IconButton>
                  </Tooltip>
                </Box>
              ))}
            </Box>
          )}
        </Box>
      </Collapse>
    </Paper>
  );
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }): React.JSX.Element {
  return (
    <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, flexWrap: 'wrap' }}>
      <Typography
        sx={{
          flex: '0 0 130px',
          fontSize: FONT_SIZE.xs,
          color: 'text.secondary',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
          fontWeight: 600,
        }}
      >
        {label}
      </Typography>
      <Typography
        sx={{
          flex: 1,
          minWidth: 0,
          fontSize: FONT_SIZE.sm,
          color: 'text.primary',
          fontFamily: mono ? 'var(--font-mono)' : 'inherit',
          wordBreak: 'break-all',
        }}
      >
        {value}
      </Typography>
    </Box>
  );
}
