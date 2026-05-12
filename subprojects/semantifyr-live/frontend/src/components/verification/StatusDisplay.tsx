/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Tooltip from '@mui/material/Tooltip';
import CircularProgress from '@mui/material/CircularProgress';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import ErrorOutlinedIcon from '@mui/icons-material/ErrorOutlined';
import RadioButtonUncheckedOutlinedIcon from '@mui/icons-material/RadioButtonUncheckedOutlined';
import HelpOutlineOutlinedIcon from '@mui/icons-material/HelpOutlineOutlined';
import RemoveCircleOutlineOutlinedIcon from '@mui/icons-material/RemoveCircleOutlineOutlined';
import UpdateIcon from '@mui/icons-material/Update';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import type { VerificationCaseStatus, VerificationCaseState, VerificationState } from '../../lib/verification';
import { FONT_SIZE } from '../../lib/util/theme';

export const ICON_SIZE = 20;
const iconBoxSx = { width: ICON_SIZE, height: ICON_SIZE, display: 'flex', alignItems: 'center', justifyContent: 'center' } as const;

type StatusCategory = 'passed' | 'failed' | 'errored' | 'inconclusive' | 'not_supported' | 'unknown';

function statusColorVar(category: StatusCategory): string {
  switch (category) {
    case 'passed': return 'var(--success)';
    case 'failed':
    case 'errored': return 'var(--danger)';
    case 'inconclusive': return 'var(--warning)';
    case 'not_supported':
    case 'unknown': return 'var(--text-muted)';
  }
}

export function StatusIcon({ category, size }: { category: StatusCategory; size: number }): React.JSX.Element {
  const color = statusColorVar(category);
  switch (category) {
    case 'passed':
      return <CheckCircleOutlinedIcon sx={{ fontSize: size, color }} />;
    case 'failed':
      return <CancelOutlinedIcon sx={{ fontSize: size, color }} />;
    case 'errored':
      return <ErrorOutlinedIcon sx={{ fontSize: size, color }} />;
    case 'inconclusive':
      return <HelpOutlineOutlinedIcon sx={{ fontSize: size, color }} />;
    case 'not_supported':
      return <RemoveCircleOutlineOutlinedIcon sx={{ fontSize: size, color }} />;
    case 'unknown':
      return <RadioButtonUncheckedOutlinedIcon sx={{ fontSize: Math.round(size * 0.8), color }} />;
  }
}

const STATUS_TOOLTIPS: Record<VerificationCaseStatus, string> = {
  passed: 'Passed',
  failed: 'Failed',
  errored: 'Error during verification',
  inconclusive: 'Inconclusive',
  not_supported: 'Not supported',
  running: 'Running',
  queued: 'Queued',
  stale: 'Not yet verified',
};

export function CaseStatusIcon({ status }: { status: VerificationCaseStatus }): React.JSX.Element {
  const tooltip = STATUS_TOOLTIPS[status];
  const icon = (() => {
    switch (status) {
      case 'passed':
      case 'failed':
      case 'errored':
      case 'inconclusive':
      case 'not_supported':
      case 'stale': {
        const category: StatusCategory = status === 'stale' ? 'unknown' : status;
        return <StatusIcon category={category} size={ICON_SIZE} />;
      }
      case 'running':
        return <CircularProgress size={16} sx={{ color: 'var(--warning)' }} />;
      case 'queued':
        return <UpdateIcon sx={{ fontSize: ICON_SIZE, color: 'var(--warning)' }} />;
    }
  })();
  return <Tooltip title={tooltip}><Box sx={iconBoxSx}>{icon}</Box></Tooltip>;
}

export interface CaseCounts {
  failed: number;
  errored: number;
  inconclusive: number;
  notSupported: number;
  passed: number;
  unknown: number;
  total: number;
}

export function countCases(cases: readonly VerificationCaseState[]): CaseCounts {
  let failed = 0;
  let errored = 0;
  let inconclusive = 0;
  let notSupported = 0;
  let passed = 0;
  for (const c of cases) {
    if (c.status === 'passed') passed++;
    else if (c.status === 'failed') failed++;
    else if (c.status === 'errored') errored++;
    else if (c.status === 'inconclusive') inconclusive++;
    else if (c.status === 'not_supported') notSupported++;
  }
  const decided = passed + failed + errored + inconclusive + notSupported;
  return {
    failed,
    errored,
    inconclusive,
    notSupported,
    passed,
    unknown: cases.length - decided,
    total: cases.length,
  };
}

const COUNT_TOOLTIPS: Record<StatusCategory, string> = {
  passed: 'Passed cases',
  failed: 'Failed cases',
  errored: 'Errored cases',
  inconclusive: 'Inconclusive cases',
  not_supported: 'Cases not supported by the chosen portfolio',
  unknown: 'Not yet verified',
};

function CountBadge({ category, count }: { category: StatusCategory; count: number }): React.JSX.Element {
  const color = statusColorVar(category);
  return (
    <Tooltip title={`${count} ${COUNT_TOOLTIPS[category]}`}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.25, opacity: count === 0 ? 0.4 : 1 }}>
        <StatusIcon category={category} size={12} />
        <Typography variant="caption" sx={{ fontSize: FONT_SIZE.sm, color, fontWeight: 600 }}>{count}</Typography>
      </Box>
    </Tooltip>
  );
}

export function SummaryCounts({ cases }: { cases: readonly VerificationCaseState[] }): React.JSX.Element {
  const { failed, errored, inconclusive, notSupported, passed, unknown, total } = countCases(cases);
  if (total === 0) return <></>;
  const unresolved = unknown + inconclusive + notSupported;
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <CountBadge category="unknown" count={unresolved} />
      <CountBadge category="failed" count={failed + errored} />
      <CountBadge category="passed" count={passed} />
      <Typography variant="caption" sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}> / {total}</Typography>
    </Box>
  );
}

export function SummaryStatusIcon({ cases, phase }: { cases: readonly VerificationCaseState[]; phase: VerificationState['phase'] }): React.JSX.Element {
  if (phase === 'running') {
    return <Tooltip title="Verification in progress"><Box sx={iconBoxSx}><UpdateIcon sx={{ fontSize: ICON_SIZE, color: 'var(--warning)' }} /></Box></Tooltip>;
  }
  const { passed, failed, errored } = countCases(cases);
  if (passed === cases.length && cases.length > 0) {
    return <Tooltip title="All cases passed"><Box sx={iconBoxSx}><DoneAllIcon sx={{ fontSize: ICON_SIZE, color: 'var(--success)' }} /></Box></Tooltip>;
  }
  if (errored > 0 && failed === 0) {
    return <Tooltip title={`${errored} case${errored === 1 ? '' : 's'} errored`}><Box sx={iconBoxSx}><StatusIcon category="errored" size={ICON_SIZE} /></Box></Tooltip>;
  }
  if (failed > 0 || errored > 0) {
    const parts = [failed > 0 ? `${failed} failed` : '', errored > 0 ? `${errored} errored` : ''].filter(Boolean).join(', ');
    return <Tooltip title={parts}><Box sx={iconBoxSx}><StatusIcon category="failed" size={ICON_SIZE} /></Box></Tooltip>;
  }
  return <Tooltip title="No results yet"><Box sx={iconBoxSx}><StatusIcon category="unknown" size={ICON_SIZE} /></Box></Tooltip>;
}
