/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import Chip from '@mui/material/Chip';
import type { SessionInfo } from '../../lib/adminApi';
import SessionCard from './SessionCard';

interface Props {
  sessions: readonly SessionInfo[];
  onCancelSession: (sessionId: string) => void;
  onCancelVerification: (sessionId: string, requestId: string) => void;
}

function groupByIp(sessions: readonly SessionInfo[]): Map<string, SessionInfo[]> {
  const byIp = new Map<string, SessionInfo[]>();
  for (const s of sessions) {
    const list = byIp.get(s.remoteIp) ?? [];
    list.push(s);
    byIp.set(s.remoteIp, list);
  }
  return byIp;
}

export default function SessionList({ sessions, onCancelSession, onCancelVerification }: Props): React.JSX.Element {
  if (sessions.length === 0) {
    return (
      <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)', mb: 3 }}>
        <Typography sx={{ p: 2, color: 'var(--text-muted)', fontStyle: 'italic', fontSize: '0.85rem' }}>
          No active sessions
        </Typography>
      </Paper>
    );
  }

  const byIp = groupByIp(sessions);

  return (
    <>
      {[...byIp.entries()].map(([ip, ipSessions]) => (
        <Box key={ip} sx={{ mb: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
            <Typography sx={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text)' }}>{ip}</Typography>
            <Chip
              label={`${ipSessions.length} session${ipSessions.length === 1 ? '' : 's'}`}
              size="small"
              sx={{ bgcolor: 'var(--surface-panel-bg)', color: 'var(--text-muted)', fontWeight: 600, fontSize: '0.75rem' }}
            />
          </Box>
          {ipSessions.map((session) => (
            <SessionCard
              key={session.sessionId}
              session={session}
              onCancelSession={() => onCancelSession(session.sessionId)}
              onCancelVerification={(reqId) => onCancelVerification(session.sessionId, reqId)}
            />
          ))}
        </Box>
      ))}
    </>
  );
}
