/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import HighlightOffOutlinedIcon from '@mui/icons-material/HighlightOffOutlined';
import { formatIsoDuration } from '../../lib/util/duration';
import type {
  AdminConfigResponse,
  FlavorInfo,
  InfoResponse,
  PortfolioInfo,
} from '../../lib/api';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  info: InfoResponse;
  config: AdminConfigResponse;
  frontendCommit: string;
  frontendBuildTime: string;
  portfolios: readonly PortfolioInfo[];
  flavors: readonly FlavorInfo[];
  sessions: readonly { flavorId: string }[];
}

interface RowProps {
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}

function Row({ label, value, mono }: RowProps): React.JSX.Element {
  return (
    <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, py: 0.25, flexWrap: 'wrap' }}>
      <Typography
        sx={{
          flex: { xs: '0 0 100%', sm: '0 0 170px' },
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

function Card({ title, children }: { title: string; children: React.ReactNode }): React.JSX.Element {
  return (
    <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)', height: '100%' }}>
      <Box sx={{ p: 1.5 }}>
        <Typography
          sx={{
            fontSize: FONT_SIZE.xs,
            color: 'text.secondary',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            fontWeight: 700,
            mb: 0.75,
          }}
        >
          {title}
        </Typography>
        {children}
      </Box>
    </Paper>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KiB`;
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)} MiB`;
  }
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GiB`;
}

function renderOptional(value: string | null): React.ReactNode {
  if (value === null || value === '') {
    return <Box component="span" sx={{ color: 'text.secondary', fontStyle: 'italic' }}>not set</Box>;
  }
  return value;
}

function renderBool(value: boolean): React.ReactNode {
  return (
    <Chip
      label={value ? 'yes' : 'no'}
      size="small"
      sx={{
        height: 20,
        fontWeight: 600,
        fontSize: FONT_SIZE.xs,
        bgcolor: value ? 'var(--success-soft-bg, rgba(74,222,128,0.16))' : 'var(--surface-panel-bg)',
        color: value ? 'var(--success)' : 'text.secondary',
      }}
    />
  );
}

export default function ConfigTab({
  info,
  config,
  frontendCommit,
  frontendBuildTime,
  portfolios,
  flavors,
  sessions,
}: Props): React.JSX.Element {
  const flavorCounts = new Map<string, number>();
  for (const s of sessions) {
    flavorCounts.set(s.flavorId, (flavorCounts.get(s.flavorId) ?? 0) + 1);
  }

  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card title="Backend build">
          <Row label="Commit" value={info.commit} mono />
          <Row label="Built" value={info.buildTime} />
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card title="Frontend build">
          <Row label="Commit" value={frontendCommit} mono />
          <Row label="Built" value={frontendBuildTime} />
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card title="Server">
          <Row label="Development mode" value={renderBool(config.development)} />
          <Row label="Port" value={String(config.server.port)} />
          <Row label="Admin password set" value={renderBool(config.server.adminPasswordSet)} />
          <Row label="HTTPS-only cookies" value={renderBool(config.server.httpsOnlyCookies)} />
          <Row label="Ping period" value={formatIsoDuration(config.server.pingPeriod)} />
          <Row label="Ping timeout" value={formatIsoDuration(config.server.pingTimeout)} />
          <Row label="Session idle timeout" value={formatIsoDuration(config.server.sessionIdleTimeout)} />
          <Row
            label="WS handshakes / period"
            value={`${config.server.wsHandshakesPerPeriod} per ${formatIsoDuration(config.server.wsHandshakeRatePeriod)}`}
          />
          <Row label="Max WS frame" value={formatBytes(config.server.maxWsFrameSize)} />
          <Row label="Web root" value={renderOptional(config.server.webRootDirectory)} mono />
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card title="Sessions and verification">
          <Row label="Max sessions" value={String(config.sessionManager.maxSessionsGlobal)} />
          <Row label="Verifier concurrency" value={String(config.verification.concurrency)} />
          <Row label="Verifier timeout" value={formatIsoDuration(config.verification.timeout)} />
          <Row label="Work root" value={config.sessionManager.rootWorkDirectory} mono />
          <Row
            label="Semantic libraries"
            value={renderOptional(config.sessionManager.semanticLibrariesDirectory)}
            mono
          />
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card title="Portfolios">
          <PortfolioAvailability portfolios={portfolios} />
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 6 }}>
        <Card title="Flavors">
          <FlavorList flavors={flavors} flavorCounts={flavorCounts} />
        </Card>
      </Grid>
    </Grid>
  );
}

function PortfolioAvailability({ portfolios }: { portfolios: readonly PortfolioInfo[] }): React.JSX.Element {
  if (portfolios.length === 0) {
    return (
      <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary', fontStyle: 'italic' }}>
        No portfolios reported.
      </Typography>
    );
  }
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.4 }}>
      {portfolios.map((p) => (
        <Box key={p.id} sx={{ display: 'flex', alignItems: 'center', gap: 0.75, py: 0.15 }}>
          <Tooltip title={p.available ? 'Backend available' : p.description || 'Backend unavailable'}>
            {p.available ? (
              <CheckCircleOutlinedIcon sx={{ fontSize: ICON_SIZE.sm, color: 'var(--success)' }} />
            ) : (
              <HighlightOffOutlinedIcon sx={{ fontSize: ICON_SIZE.sm, color: 'var(--danger)' }} />
            )}
          </Tooltip>
          <Typography sx={{ fontSize: FONT_SIZE.sm, color: p.available ? 'var(--text)' : 'var(--text-muted)', flex: 1, minWidth: 0 }}>
            {p.displayName}
          </Typography>
          <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary', fontFamily: 'var(--font-mono)' }}>
            {p.id}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

function FlavorList({
  flavors,
  flavorCounts,
}: {
  flavors: readonly FlavorInfo[];
  flavorCounts: Map<string, number>;
}): React.JSX.Element {
  if (flavors.length === 0) {
    return (
      <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary', fontStyle: 'italic' }}>
        No flavors reported.
      </Typography>
    );
  }
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.4 }}>
      {flavors.map((f) => {
        const count = flavorCounts.get(f.id) ?? 0;
        return (
          <Box key={f.id} sx={{ display: 'flex', alignItems: 'center', gap: 0.75, py: 0.15, flexWrap: 'wrap' }}>
            <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.primary', flex: 1, minWidth: 0 }}>
              {f.displayName}
            </Typography>
            <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary', fontFamily: 'var(--font-mono)' }}>
              {f.id}
            </Typography>
            <Tooltip title={count === 0 ? 'No active sessions' : `${count} active session${count === 1 ? '' : 's'}`}>
              <Chip
                label={String(count)}
                size="small"
                sx={{
                  height: 18,
                  fontSize: FONT_SIZE.xs,
                  fontWeight: 600,
                  bgcolor: count > 0 ? 'var(--surface-panel-bg)' : 'transparent',
                  color: count > 0 ? 'text.primary' : 'text.secondary',
                  border: '1px solid var(--surface-border)',
                }}
              />
            </Tooltip>
          </Box>
        );
      })}
    </Box>
  );
}
