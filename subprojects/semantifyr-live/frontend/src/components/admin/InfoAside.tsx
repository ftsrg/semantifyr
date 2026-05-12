/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import HighlightOffOutlinedIcon from '@mui/icons-material/HighlightOffOutlined';
import { formatIsoDuration } from '../../lib/util/duration';
import type { AdminConfigResponse, InfoResponse } from '../../lib/api';
import type { PortfolioInfo } from '../../lib/api';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  info: InfoResponse;
  config: AdminConfigResponse;
  frontendCommit: string;
  frontendBuildTime: string;
  portfolios: readonly PortfolioInfo[];
}

interface RowProps {
  label: string;
  value: React.ReactNode;
  tooltip?: string;
  mono?: boolean;
}

function Row({ label, value, mono }: RowProps): React.JSX.Element {
  return (
    <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, py: 0.25 }}>
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

function Section({ title, children }: { title: string; children: React.ReactNode }): React.JSX.Element {
  return (
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
  );
}

export default function InfoAside({ info, config, frontendCommit, frontendBuildTime, portfolios }: Props): React.JSX.Element {
  return (
    <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)' }}>
      <Section title="Available portfolios">
        <PortfolioAvailability portfolios={portfolios} />
      </Section>
      <Box sx={{ borderTop: '1px solid var(--surface-border)' }} />
      <Section title="Backend build">
        <Row label="Commit" value={info.commit} mono />
        <Row label="Built" value={info.buildTime} />
      </Section>
      <Box sx={{ borderTop: '1px solid var(--surface-border)' }} />
      <Section title="Frontend build">
        <Row label="Commit" value={frontendCommit} mono />
        <Row label="Built" value={frontendBuildTime} />
      </Section>
      <Box sx={{ borderTop: '1px solid var(--surface-border)' }} />
      <Section title="Configuration">
        <Row label="Sessions (global)" value={String(config.maxSessionsGlobal)} />
        <Row label="Sessions (per IP)" value={String(config.maxSessionsPerIp)} />
        <Row label="Verifier concurrency" value={String(config.verificationConcurrency)} />
        <Row label="Verifier timeout" value={formatIsoDuration(config.verificationTimeout)} />
      </Section>
    </Paper>
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
          <Typography sx={{ fontSize: FONT_SIZE.sm, color: p.available ? 'var(--text)' : 'var(--text-muted)', flex: 1 }}>
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
