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
  onCancelVerification: (sessionId: string, requestId: string) => void;
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

function shortenSessionId(id: string): string {
  // UUIDv4 ids are noisy at full width; the leading 8 chars are enough to disambiguate in
  // practice and stay readable in the table. The full id is one expand-row away.
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

interface SessionRowProps {
  session: SessionInfo;
  onCancelSession: (sessionId: string) => void;
  onCancelVerification: (sessionId: string, requestId: string) => void;
}

function SessionRow({ session, onCancelSession, onCancelVerification }: SessionRowProps): React.JSX.Element {
  const [open, setOpen] = useState(false);
  const inFlight = session.activeVerifications.length;
  const startedColor = session.started ? 'var(--success)' : 'var(--text-muted)';
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
          <Tooltip title={session.started ? 'LSP server is running' : 'Session is starting'}>
            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
              <Box component="span" sx={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', bgcolor: startedColor }} />
              <Typography component="span" sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>
                {session.started ? 'running' : 'starting'}
              </Typography>
            </Box>
          </Tooltip>
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
        <TableCell sx={cellSx}>{session.remoteIp}</TableCell>
        <TableCell sx={cellSx}>{formatIsoDuration(session.uptime)}</TableCell>
        <TableCell sx={cellSx}>
          <Tooltip
            title={`Last client message ${formatIsoDuration(session.bridgeInfo.timeSinceLastClientMessage)} ago · last server message ${formatIsoDuration(session.bridgeInfo.timeSinceLastServerMessage)} ago`}
          >
            <Box component="span">
              {formatIsoDuration(session.bridgeInfo.timeSinceLastClientMessage)} / {formatIsoDuration(session.bridgeInfo.timeSinceLastServerMessage)}
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
        <TableCell colSpan={9} sx={{ p: 0, borderColor: 'var(--surface-border)', borderBottom: open ? undefined : 'none' }}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <SessionDetails
              session={session}
              onCancelVerification={(reqId) => onCancelVerification(session.sessionId, reqId)}
            />
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

interface SessionDetailsProps {
  session: SessionInfo;
  onCancelVerification: (requestId: string) => void;
}

function SessionDetails({ session, onCancelVerification }: SessionDetailsProps): React.JSX.Element {
  const errorBadge =
    session.bridgeInfo.errorCount > 0 ? (
      <Chip
        label={`${session.bridgeInfo.errorCount} error${session.bridgeInfo.errorCount === 1 ? '' : 's'}`}
        size="small"
        sx={{ bgcolor: 'var(--danger-soft-bg)', color: 'var(--danger)', fontWeight: 600, fontSize: FONT_SIZE.xs, height: 20 }}
      />
    ) : null;
  return (
    <Box sx={{ bgcolor: 'var(--surface-panel-bg)', px: 3, py: 1.5, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, alignItems: 'flex-start' }}>
        <DetailGroup label="Session id" value={session.sessionId} mono />
        <DetailGroup label="Working directory" value={session.workingDirectory} mono />
        <DetailGroup
          label="Messages (client / server)"
          value={`${session.bridgeInfo.clientMessageCount} / ${session.bridgeInfo.serverMessageCount}`}
        />
        {errorBadge && <Box sx={{ alignSelf: 'flex-end' }}>{errorBadge}</Box>}
      </Box>
      {session.activeVerifications.length > 0 && (
        <Box>
          <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.04em', fontWeight: 600, mb: 0.5 }}>
            In-flight verifications
          </Typography>
          {session.activeVerifications.map((v) => (
            <Box
              key={v.requestId}
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
              <KindChip kind={v.kind ?? 'Verify'} />
              {v.portfolioId && (
                <Chip
                  label={v.portfolioId}
                  size="small"
                  sx={{ bgcolor: 'var(--surface-bg)', color: 'text.secondary', fontWeight: 500, fontSize: FONT_SIZE.xs, height: 20, fontFamily: 'inherit' }}
                />
              )}
              {v.caseLabel && (
                <Box component="span" sx={{ color: 'text.primary', fontFamily: 'inherit', fontSize: FONT_SIZE.sm }}>
                  {v.caseLabel}
                </Box>
              )}
              <Tooltip title={v.requestId}>
                <Box component="span" sx={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', color: 'text.secondary' }}>
                  #{v.requestId}
                </Box>
              </Tooltip>
              {v.elapsed && (
                <Box component="span" sx={{ color: 'text.secondary', fontFamily: 'inherit', fontSize: FONT_SIZE.xs }}>
                  {formatIsoDuration(v.elapsed)}
                </Box>
              )}
              <Tooltip title="Cancel">
                <IconButton size="small" onClick={() => onCancelVerification(v.requestId)} sx={{ color: 'var(--danger)' }}>
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
      <Table size="small" sx={{ minWidth: 760, '& td, & th': { borderBottomColor: 'var(--surface-border)' } }}>
        <TableHead>
          <TableRow>
            <TableCell sx={{ ...headerCellSx, width: 32 }} />
            <TableCell sx={headerCellSx}>Status</TableCell>
            <TableCell sx={headerCellSx}>Flavor</TableCell>
            <TableCell sx={headerCellSx}>Session</TableCell>
            <TableCell sx={headerCellSx}>Remote IP</TableCell>
            <TableCell sx={headerCellSx}>Uptime</TableCell>
            <TableCell sx={headerCellSx}>Idle (client / server)</TableCell>
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
