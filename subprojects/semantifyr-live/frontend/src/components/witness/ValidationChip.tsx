/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import CircularProgress from '@mui/material/CircularProgress';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import ErrorOutlinedIcon from '@mui/icons-material/ErrorOutlined';
import HelpOutlineOutlinedIcon from '@mui/icons-material/HelpOutlineOutlined';
import type { WitnessValidationStatus } from '../../lib/verification';

interface Props {
  status: WitnessValidationStatus | undefined;
  busy: boolean;
  disabled?: boolean;
  onRevalidate: () => void;
}

function chipDescriptor(status: WitnessValidationStatus | undefined): {
  label: string;
  color: string;
  icon: React.ReactElement;
  tooltip: string;
} {
  switch (status) {
    case 'valid':
      return {
        label: 'Witness valid',
        color: 'var(--success)',
        icon: <CheckCircleOutlinedIcon sx={{ fontSize: 16 }} />,
        tooltip: 'The witness was re-verified successfully across every available backend (cross-check).',
      };
    case 'invalid':
      return {
        label: 'Witness invalid',
        color: 'var(--danger)',
        icon: <ErrorOutlinedIcon sx={{ fontSize: 16 }} />,
        tooltip: 'Re-verifying the witness disagrees with the original verdict.',
      };
    case 'inconclusive':
      return {
        label: 'Inconclusive',
        color: 'var(--warning)',
        icon: <HelpOutlineOutlinedIcon sx={{ fontSize: 16 }} />,
        tooltip: 'The cross-validation could not produce a decisive verdict.',
      };
    case 'errored':
      return {
        label: 'Validation errored',
        color: 'var(--danger)',
        icon: <ErrorOutlinedIcon sx={{ fontSize: 16 }} />,
        tooltip: 'An error occurred while re-validating the witness.',
      };
    default:
      return {
        label: 'Not validated',
        color: 'var(--text-muted)',
        icon: <HelpOutlineOutlinedIcon sx={{ fontSize: 16 }} />,
        tooltip: 'Trigger re-validation to cross-check the witness on every available backend.',
      };
  }
}

export default function ValidationChip({ status, busy, disabled, onRevalidate }: Props): React.JSX.Element {
  const descriptor = chipDescriptor(status);
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, px: 1, py: 0.5, borderBottom: '1px solid var(--surface-border)', bgcolor: 'var(--surface-toolbar-bg)' }}>
      <Tooltip title={descriptor.tooltip}>
        <Chip
          icon={busy ? <CircularProgress size={12} sx={{ color: descriptor.color }} /> : descriptor.icon}
          label={busy ? 'Validating...' : descriptor.label}
          size="small"
          sx={{
            bgcolor: 'transparent',
            color: descriptor.color,
            border: `1px solid ${descriptor.color}`,
            '& .MuiChip-icon': { color: descriptor.color },
          }}
        />
      </Tooltip>
      <Box sx={{ flex: 1 }} />
      <Tooltip title="Re-validate witness">
        <span>
          <IconButton size="small" onClick={onRevalidate} disabled={disabled || busy} sx={{ color: 'var(--text-muted)' }}>
            <RefreshIcon sx={{ fontSize: 18 }} />
          </IconButton>
        </span>
      </Tooltip>
    </Box>
  );
}
