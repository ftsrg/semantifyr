/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import LinearProgress from '@mui/material/LinearProgress';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import ReportGmailerrorredOutlinedIcon from '@mui/icons-material/ReportGmailerrorredOutlined';
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined';
import { formatIsoDuration } from '../../lib/duration';
import type { SessionInfo } from '../../lib/adminApi';

interface Props {
  uptime: string;
  activeSessions: number;
  maxSessions: number;
  sessions: readonly SessionInfo[];
}

interface TileProps {
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  icon: React.ReactNode;
  accent?: 'default' | 'warning' | 'danger' | 'success';
  tooltip?: string;
}

const ACCENT_VARS: Record<NonNullable<TileProps['accent']>, string> = {
  default: 'var(--text)',
  warning: 'var(--warning)',
  danger: 'var(--danger)',
  success: 'var(--success)',
};

function Tile({ label, value, hint, icon, accent = 'default', tooltip }: TileProps): React.JSX.Element {
  const valueColor = ACCENT_VARS[accent];
  const content = (
    <Box
      sx={{
        flex: '1 1 0',
        minWidth: 180,
        bgcolor: 'var(--surface-bg)',
        border: '1px solid var(--surface-border)',
        borderRadius: 1,
        px: 2,
        py: 1.25,
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Box sx={{ color: 'var(--text-muted)', display: 'inline-flex' }}>{icon}</Box>
        <Typography
          sx={{
            fontSize: '0.7rem',
            color: 'var(--text-muted)',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            fontWeight: 600,
          }}
        >
          {label}
        </Typography>
      </Box>
      <Typography sx={{ fontSize: '1.4rem', fontWeight: 700, color: valueColor, lineHeight: 1.1 }}>
        {value}
      </Typography>
      {hint !== undefined && hint !== null && (
        <Box sx={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>{hint}</Box>
      )}
    </Box>
  );
  return tooltip ? <Tooltip title={tooltip}>{content}</Tooltip> : content;
}

export default function KpiStrip({ uptime, activeSessions, maxSessions, sessions }: Props): React.JSX.Element {
  const sessionLoad = maxSessions > 0 ? (activeSessions / maxSessions) * 100 : 0;
  const sessionsAccent: TileProps['accent'] = sessionLoad >= 90 ? 'danger' : sessionLoad >= 60 ? 'warning' : 'default';

  const activeVerifications = sessions.reduce((sum, s) => sum + s.activeVerifications.length, 0);
  const errorCount = sessions.reduce((sum, s) => sum + s.bridgeInfo.errorCount, 0);

  const flavorCounts = new Map<string, number>();
  for (const s of sessions) {
    flavorCounts.set(s.flavorId, (flavorCounts.get(s.flavorId) ?? 0) + 1);
  }
  const flavorBreakdown = [...flavorCounts.entries()].sort((a, b) => b[1] - a[1]);

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mx: 3, mt: 2 }}>
      <Tile
        label="Sessions"
        icon={<GroupOutlinedIcon sx={{ fontSize: 18 }} />}
        accent={sessionsAccent}
        tooltip={`${activeSessions} of ${maxSessions} concurrent sessions in use across all clients.`}
        value={
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
            <Box component="span">{activeSessions}</Box>
            <Box component="span" sx={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 500 }}>
              / {maxSessions}
            </Box>
          </Box>
        }
        hint={
          <Box>
            <LinearProgress
              variant="determinate"
              value={Math.min(sessionLoad, 100)}
              sx={{
                mt: 0.5,
                height: 4,
                borderRadius: 2,
                bgcolor: 'var(--surface-border)',
                '& .MuiLinearProgress-bar': { bgcolor: ACCENT_VARS[sessionsAccent] },
              }}
            />
            {flavorBreakdown.length > 0 && (
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
                {flavorBreakdown.map(([flavorId, count]) => (
                  <Tooltip key={flavorId} title={`${count} session${count === 1 ? '' : 's'} on flavor ${flavorId}`}>
                    <Box
                      component="span"
                      sx={{
                        fontSize: '0.65rem',
                        fontWeight: 600,
                        color: 'var(--text-muted)',
                        bgcolor: 'var(--surface-panel-bg)',
                        border: '1px solid var(--surface-border)',
                        borderRadius: 0.5,
                        px: 0.5,
                        py: 0.1,
                      }}
                    >
                      {flavorId} {count}
                    </Box>
                  </Tooltip>
                ))}
              </Box>
            )}
          </Box>
        }
      />
      <Tile
        label="Active verifications"
        icon={<PlayCircleOutlineIcon sx={{ fontSize: 18 }} />}
        value={activeVerifications}
        accent={activeVerifications > 0 ? 'warning' : 'default'}
        tooltip="Verifications currently in flight across every session."
        hint={
          activeVerifications === 0
            ? 'Idle'
            : `Across ${sessions.filter((s) => s.activeVerifications.length > 0).length} session(s)`
        }
      />
      <Tile
        label="Errors (cumulative)"
        icon={<ReportGmailerrorredOutlinedIcon sx={{ fontSize: 18 }} />}
        value={errorCount}
        accent={errorCount > 0 ? 'danger' : 'success'}
        tooltip="Sum of LSP-proxy error counts across every live session."
        hint={errorCount > 0 ? 'Inspect the offending session below' : 'No errors recorded'}
      />
      <Tile
        label="Backend uptime"
        icon={<ScheduleOutlinedIcon sx={{ fontSize: 18 }} />}
        value={formatIsoDuration(uptime)}
        tooltip={`Raw uptime: ${uptime}`}
      />
    </Box>
  );
}
