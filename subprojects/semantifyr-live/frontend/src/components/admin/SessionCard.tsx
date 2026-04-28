/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import StopIcon from '@mui/icons-material/Stop';
import type { SessionInfo } from '../../lib/adminApi';
import { formatIsoDuration } from '../../lib/duration';

const cellSx = { color: 'var(--text)', fontSize: '0.85rem', borderColor: 'var(--surface-border)' } as const;
const labelSx = { ...cellSx, color: 'var(--text-muted)', fontWeight: 600 } as const;
const monoSx = { ...cellSx, fontFamily: 'var(--font-mono)', fontSize: '0.8rem' } as const;

interface Props {
  session: SessionInfo;
  onCancelSession: () => void;
  onCancelVerification: (requestId: string) => void;
}

export default function SessionCard({ session, onCancelSession, onCancelVerification }: Props): React.JSX.Element {
  return (
    <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)', mb: 1.5 }}>
      <Box sx={{ px: 2, py: 1, display: 'flex', alignItems: 'center', gap: 1, borderBottom: session.activeVerifications.length > 0 ? '1px solid var(--surface-border-soft)' : 'none' }}>
        <Chip label={session.flavorId} size="small" sx={{ bgcolor: 'rgba(251,191,36,0.15)', color: 'var(--warning)', fontWeight: 600 }} />
        <Chip
          label={session.started ? 'running' : 'starting'}
          size="small"
          sx={{
            bgcolor: session.started ? 'rgba(74,222,128,0.15)' : 'rgba(155,155,155,0.15)',
            color: session.started ? 'var(--success)' : 'var(--text-muted)',
            fontWeight: 600, fontSize: '0.7rem',
          }}
        />
        <Typography sx={{ flex: 1, fontFamily: 'var(--font-mono)', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
          {session.sessionId}
        </Typography>
        <Tooltip title="Kill session">
          <IconButton size="small" onClick={onCancelSession} sx={{ color: 'var(--danger)' }}>
            <StopIcon sx={{ fontSize: 18 }} />
          </IconButton>
        </Tooltip>
      </Box>

      <Box sx={{ px: 2, py: 0.75 }}>
        <Table size="small">
          <TableBody>
            <TableRow>
              <TableCell sx={labelSx}>Uptime</TableCell>
              <TableCell sx={cellSx}>{formatIsoDuration(session.uptime)}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell sx={labelSx}>Working directory</TableCell>
              <TableCell sx={monoSx}>{session.workingDirectory}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell sx={labelSx}>Messages (client / server)</TableCell>
              <TableCell sx={cellSx}>{session.clientMessageCount} / {session.serverMessageCount}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell sx={labelSx}>Last activity (client / server)</TableCell>
              <TableCell sx={cellSx}>{formatIsoDuration(session.timeSinceLastClientMessage)} / {formatIsoDuration(session.timeSinceLastServerMessage)} ago</TableCell>
            </TableRow>
            {session.errorCount > 0 && (
              <TableRow>
                <TableCell sx={labelSx}>Errors</TableCell>
                <TableCell sx={{ ...cellSx, color: 'var(--danger)' }}>{session.errorCount}</TableCell>
              </TableRow>
            )}
            <TableRow>
              <TableCell sx={labelSx}>Active verifications</TableCell>
              <TableCell sx={cellSx}>{session.activeVerifications.length}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </Box>

      {session.activeVerifications.length > 0 && (
        <Box sx={{ px: 2, pb: 1 }}>
          <Typography sx={{ fontSize: '0.78rem', color: 'var(--text-muted)', mb: 0.5, fontWeight: 600 }}>
            In-flight verifications
          </Typography>
          {session.activeVerifications.map((reqId) => (
            <Box key={reqId} sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 0.25 }}>
              <Chip label="running" size="small" sx={{ bgcolor: 'rgba(251,191,36,0.15)', color: 'var(--warning)', fontWeight: 600, fontSize: '0.7rem' }} />
              <Typography sx={{ flex: 1, fontFamily: 'var(--font-mono)', fontSize: '0.78rem', color: 'var(--text)' }}>
                {reqId}
              </Typography>
              <Tooltip title="Cancel verification">
                <IconButton size="small" onClick={() => onCancelVerification(reqId)} sx={{ color: 'var(--danger)' }}>
                  <StopIcon sx={{ fontSize: 16 }} />
                </IconButton>
              </Tooltip>
            </Box>
          ))}
        </Box>
      )}
    </Paper>
  );
}
