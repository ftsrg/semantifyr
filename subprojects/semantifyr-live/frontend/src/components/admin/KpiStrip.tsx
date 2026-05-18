/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import LinearProgress from '@mui/material/LinearProgress';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined';
import { formatIsoDuration } from '../../lib/util/duration';
import type { SessionInfo } from '../../lib/api';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  uptime: string;
  startedAt: string;
  activeSessions: number;
  maxSessions: number;
  verificationConcurrency: number;
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
        height: '100%',
        bgcolor: 'var(--surface-bg)',
        border: '1px solid var(--surface-border)',
        borderRadius: 1,
        px: 2,
        py: 1.25,
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
        minWidth: 0,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Box sx={{ color: 'text.secondary', display: 'inline-flex' }}>{icon}</Box>
        <Typography
          sx={{
            fontSize: FONT_SIZE.xs,
            color: 'text.secondary',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            fontWeight: 600,
          }}
        >
          {label}
        </Typography>
      </Box>
      <Typography component="div" sx={{ fontSize: '1.4rem', fontWeight: 700, color: valueColor, lineHeight: 1.1 }}>
        {value}
      </Typography>
      {hint !== undefined && hint !== null && (
        <Box sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}>{hint}</Box>
      )}
    </Box>
  );
  return tooltip ? <Tooltip title={tooltip}>{content}</Tooltip> : content;
}

function formatStartedAt(iso: string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return iso;
  }
  return parsed.toLocaleString();
}

export default function KpiStrip({
  uptime,
  startedAt,
  activeSessions,
  maxSessions,
  verificationConcurrency,
  sessions,
}: Props): React.JSX.Element {
  const sessionLoad = maxSessions > 0 ? (activeSessions / maxSessions) * 100 : 0;
  const sessionsAccent: TileProps['accent'] = sessionLoad >= 90 ? 'danger' : sessionLoad >= 60 ? 'warning' : 'default';

  const runningVerifications = sessions.reduce(
    (sum, s) => sum + s.activeVerifications.filter((v) => v.state === 'Running').length,
    0,
  );
  const queuedVerifications = sessions.reduce(
    (sum, s) => sum + s.activeVerifications.filter((v) => v.state === 'Queued').length,
    0,
  );
  const activeVerifications = runningVerifications + queuedVerifications;

  const flavorCounts = new Map<string, number>();
  for (const s of sessions) {
    flavorCounts.set(s.flavorId, (flavorCounts.get(s.flavorId) ?? 0) + 1);
  }
  const flavorBreakdown = [...flavorCounts.entries()].sort((a, b) => b[1] - a[1]);

  return (
    <Grid container spacing={2} sx={{ mx: 0, mt: 2, px: 3, width: '100%' }}>
      <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
        <Tile
          label="Sessions"
          icon={<GroupOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />}
          accent={sessionsAccent}
          tooltip={`${activeSessions} of ${maxSessions} concurrent sessions in use across all clients.`}
          value={
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
              <Box component="span">{activeSessions}</Box>
              <Box component="span" sx={{ fontSize: FONT_SIZE.md, color: 'text.secondary', fontWeight: 500 }}>
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
                          fontSize: FONT_SIZE.xs,
                          fontWeight: 600,
                          color: 'text.secondary',
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
      </Grid>
      <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
        <Tile
          label="Active verifications"
          icon={<PlayCircleOutlineIcon sx={{ fontSize: ICON_SIZE.md }} />}
          value={queuedVerifications > 0 ? `${runningVerifications}+${queuedVerifications}` : runningVerifications}
          accent={activeVerifications > 0 ? 'warning' : 'default'}
          tooltip={
            queuedVerifications > 0
              ? `${runningVerifications} running, ${queuedVerifications} queued, cap ${verificationConcurrency} concurrent`
              : `${runningVerifications} running, cap ${verificationConcurrency} concurrent`
          }
          hint={
            activeVerifications === 0
              ? `Idle (cap ${verificationConcurrency})`
              : `Across ${sessions.filter((s) => s.activeVerifications.length > 0).length} session(s), cap ${verificationConcurrency}`
          }
        />
      </Grid>
      <Grid size={{ xs: 12, sm: 12, lg: 4 }}>
        <Tile
          label="Backend uptime"
          icon={<ScheduleOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />}
          value={formatIsoDuration(uptime)}
          tooltip={`Started ${formatStartedAt(startedAt)}`}
          hint={`Started ${formatStartedAt(startedAt)}`}
        />
      </Grid>
    </Grid>
  );
}
