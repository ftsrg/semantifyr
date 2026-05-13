/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useState } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
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

const headerCellSx = {
  color: 'text.secondary',
  fontSize: FONT_SIZE.xs,
  textTransform: 'uppercase',
  letterSpacing: '0.04em',
  fontWeight: 600,
  borderColor: 'var(--surface-border)',
  py: 0.75,
} as const;

const cellSx = {
  color: 'text.primary',
  fontSize: FONT_SIZE.md,
  borderColor: 'var(--surface-border)',
  py: 0.5,
} as const;

const monoSx = {
  ...cellSx,
  fontFamily: 'var(--font-mono)',
  fontSize: FONT_SIZE.sm,
  color: 'text.secondary',
} as const;

const hideBelowLg = { display: { xs: 'none', lg: 'table-cell' } } as const;

function shortenSessionId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

interface SessionRowProps {
  session: SessionInfo;
  onCancelSession: (sessionId: string) => void;
  onCancelVerification: (verificationId: string) => void;
}

function SessionRow({ session, onCancelSession, onCancelVerification }: SessionRowProps): React.JSX.Element {
  const [open, setOpen] = useState(false);
  const inFlight = session.activeVerifications.length;
  return (
    <>
      <TableRow
        hover
        onClick={() => setOpen((prev) => !prev)}
        sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'var(--surface-panel-bg)' } }}
      >
        <TableCell sx={{ ...cellSx, width: 32, px: 0.5 }}>
          <IconButton size="small" sx={{ color: 'text.secondary', p: 0.25 }} onClick={(e) => { e.stopPropagation(); setOpen((prev) => !prev); }}>
            {open ? <KeyboardArrowDownIcon sx={{ fontSize: ICON_SIZE.md }} /> : <KeyboardArrowRightIcon sx={{ fontSize: ICON_SIZE.md }} />}
          </IconButton>
        </TableCell>
        <TableCell sx={cellSx}>
          <Chip
            label={session.flavorId}
            size="small"
            sx={{ bgcolor: 'var(--surface-panel-bg)', color: 'text.primary', fontWeight: 500, fontSize: FONT_SIZE.xs, height: 20 }}
          />
        </TableCell>
        <TableCell sx={monoSx}>
          <Tooltip title={session.sessionId}>
            <Box component="span">{shortenSessionId(session.sessionId)}</Box>
          </Tooltip>
        </TableCell>
        <TableCell sx={{ ...cellSx, ...hideBelowLg }}>{session.remoteIp}</TableCell>
        <TableCell sx={cellSx}>{formatIsoDuration(session.uptime)}</TableCell>
        <TableCell sx={{ ...cellSx, ...hideBelowLg }}>
          <Tooltip
            title={`Last client message ${formatIsoDuration(session.sessionLspInfo.timeSinceLastClientMessage)} ago · last server message ${formatIsoDuration(session.sessionLspInfo.timeSinceLastServerMessage)} ago`}
          >
            <Box component="span">
              {formatIsoDuration(session.sessionLspInfo.timeSinceLastClientMessage)} / {formatIsoDuration(session.sessionLspInfo.timeSinceLastServerMessage)}
            </Box>
          </Tooltip>
        </TableCell>
        <TableCell sx={cellSx}>
          {inFlight > 0 ? (
            <Chip
              label={`${inFlight} running`}
              size="small"
              sx={{ bgcolor: 'var(--warning-soft-bg)', color: 'var(--warning)', fontWeight: 600, fontSize: FONT_SIZE.xs, height: 20 }}
            />
          ) : (
            <Box component="span" sx={{ color: 'text.secondary', fontSize: FONT_SIZE.sm }}>idle</Box>
          )}
        </TableCell>
        <TableCell sx={{ ...cellSx, textAlign: 'right', pr: 1 }}>
          <Tooltip title="Kill session">
            <IconButton
              size="small"
              onClick={(e) => { e.stopPropagation(); onCancelSession(session.sessionId); }}
              sx={{ color: 'var(--danger)' }}
            >
              <StopIcon sx={{ fontSize: ICON_SIZE.md }} />
            </IconButton>
          </Tooltip>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={8} sx={{ p: 0, borderColor: 'var(--surface-border)', borderBottom: open ? undefined : 'none' }}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <SessionDetails session={session} onCancelVerification={onCancelVerification} />
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

interface SessionDetailsProps {
  session: SessionInfo;
  onCancelVerification: (verificationId: string) => void;
}

function SessionDetails({ session, onCancelVerification }: SessionDetailsProps): React.JSX.Element {
  return (
    <Box sx={{ bgcolor: 'var(--surface-panel-bg)', px: 3, py: 1.5, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, alignItems: 'flex-start' }}>
        <DetailGroup label="Session id" value={session.sessionId} mono />
        <DetailGroup label="Remote IP" value={session.remoteIp} />
        <DetailGroup
          label="Idle (client / server)"
          value={`${formatIsoDuration(session.sessionLspInfo.timeSinceLastClientMessage)} / ${formatIsoDuration(session.sessionLspInfo.timeSinceLastServerMessage)}`}
        />
        <DetailGroup label="Working directory" value={session.workingDirectory} mono />
      </Box>
      {session.activeVerifications.length > 0 && (
        <Box>
          <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.04em', fontWeight: 600, mb: 0.5 }}>
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
                color: 'text.primary',
              }}
            >
              <KindChip kind={v.kind} />
              <Chip
                label={v.portfolioId}
                size="small"
                sx={{ bgcolor: 'var(--surface-bg)', color: 'text.secondary', fontWeight: 500, fontSize: FONT_SIZE.xs, height: 20, fontFamily: 'inherit' }}
              />
              <Tooltip title={v.verificationId}>
                <Box component="span" sx={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', color: 'text.secondary' }}>
                  #{v.verificationId.slice(0, 8)}
                </Box>
              </Tooltip>
              <Box component="span" sx={{ color: 'text.secondary', fontFamily: 'inherit', fontSize: FONT_SIZE.xs }}>
                {formatIsoDuration(v.elapsed)}
              </Box>
              <Tooltip title="Cancel">
                <IconButton size="small" onClick={() => onCancelVerification(v.verificationId)} sx={{ color: 'var(--danger)' }}>
                  <StopIcon sx={{ fontSize: ICON_SIZE.sm }} />
                </IconButton>
              </Tooltip>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}

function DetailGroup({ label, value, mono }: { label: string; value: string; mono?: boolean }): React.JSX.Element {
  return (
    <Box sx={{ minWidth: 180 }}>
      <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.04em', fontWeight: 600, mb: 0.25 }}>
        {label}
      </Typography>
      <Typography sx={{ fontSize: FONT_SIZE.md, color: 'text.primary', fontFamily: mono ? 'var(--font-mono)' : 'inherit', wordBreak: 'break-all' }}>
        {value}
      </Typography>
    </Box>
  );
}

export default function SessionsTable({ sessions, onCancelSession, onCancelVerification }: Props): React.JSX.Element {
  if (sessions.length === 0) {
    return (
      <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)' }}>
        <Typography sx={{ p: 3, color: 'text.secondary', fontStyle: 'italic', fontSize: FONT_SIZE.md, textAlign: 'center' }}>
          No active sessions.
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)', overflowX: 'auto' }}>
      <Table size="small" sx={{ '& td, & th': { borderBottomColor: 'var(--surface-border)' } }}>
        <TableHead>
          <TableRow>
            <TableCell sx={{ ...headerCellSx, width: 32 }} />
            <TableCell sx={headerCellSx}>Flavor</TableCell>
            <TableCell sx={headerCellSx}>Session</TableCell>
            <TableCell sx={{ ...headerCellSx, ...hideBelowLg }}>Remote IP</TableCell>
            <TableCell sx={headerCellSx}>Uptime</TableCell>
            <TableCell sx={{ ...headerCellSx, ...hideBelowLg }}>Idle (client / server)</TableCell>
            <TableCell sx={headerCellSx}>Verifications</TableCell>
            <TableCell sx={{ ...headerCellSx, textAlign: 'right' }}>Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sessions.map((session) => (
            <SessionRow
              key={session.sessionId}
              session={session}
              onCancelSession={onCancelSession}
              onCancelVerification={onCancelVerification}
            />
          ))}
        </TableBody>
      </Table>
    </Paper>
  );
}
