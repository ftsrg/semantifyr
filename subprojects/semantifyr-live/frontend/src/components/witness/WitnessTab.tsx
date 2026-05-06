/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useState } from 'react';
import Box from '@mui/material/Box';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import type { VerificationCaseSpecification, VerificationTrace, WitnessValidationStatus } from '../../lib/verification';
import CallTraceView from './CallTraceView';
import WitnessStateView from './WitnessStateView';
import ValidationChip from './ValidationChip';

interface Props {
  caseInfo: VerificationCaseSpecification;
  trace: VerificationTrace;
  validation: WitnessValidationStatus | undefined;
  validating: boolean;
  canRevalidate: boolean;
  onRevalidate: () => void;
  verificationPortfolioLabel?: string | undefined;
}

type ViewMode = 'trace' | 'witness' | 'raw';

export default function WitnessTab({
  caseInfo,
  trace,
  validation,
  validating,
  canRevalidate,
  onRevalidate,
  verificationPortfolioLabel,
}: Props): React.JSX.Element {
  const [view, setView] = useState<ViewMode>('trace');
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 0', minHeight: 0 }}>
      <ValidationChip
        status={validation}
        busy={validating}
        disabled={!canRevalidate}
        onRevalidate={onRevalidate}
      />
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 1.5,
          py: 0.5,
          borderBottom: '1px solid var(--surface-border)',
          bgcolor: 'var(--surface-toolbar-bg)',
        }}
      >
        <Typography sx={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
          Witness for <Box component="span" sx={{ color: 'var(--text)' }}>{caseInfo.label}</Box>
        </Typography>
        {verificationPortfolioLabel && (
          <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
            verified using <Box component="span" sx={{ color: 'var(--text)' }}>{verificationPortfolioLabel}</Box>
          </Typography>
        )}
        <Box sx={{ flex: 1 }} />
        <ToggleButtonGroup
          size="small"
          value={view}
          exclusive
          onChange={(_, next) => {
            if (next) setView(next);
          }}
          sx={{
            '& .MuiToggleButton-root': {
              color: 'var(--text-muted)',
              borderColor: 'var(--surface-border)',
              textTransform: 'none',
              fontSize: '0.75rem',
              px: 1.25,
              py: 0.25,
            },
            '& .Mui-selected': {
              color: 'var(--text)',
              bgcolor: 'var(--surface-bg)',
            },
          }}
        >
          <ToggleButton value="trace">Trace</ToggleButton>
          <ToggleButton value="witness">Witness</ToggleButton>
          <ToggleButton value="raw">Raw</ToggleButton>
        </ToggleButtonGroup>
      </Box>
      <Box sx={{ flex: '1 1 auto', minHeight: 0, minWidth: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        {view === 'trace' && <CallTraceView trace={trace.callTrace} />}
        {view === 'witness' && <WitnessStateView trace={trace.witnessState} />}
        {view === 'raw' && (
          <Box sx={{ flex: '1 1 auto', minHeight: 0, minWidth: 0, overflow: 'auto', bgcolor: 'var(--page-bg)' }}>
            <Box
              component="pre"
              sx={{
                m: 0,
                p: 1.5,
                display: 'block',
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: '0.85rem',
                color: 'var(--text)',
                whiteSpace: 'pre',
              }}
            >
              {trace.backAnnotatedSource ?? '// Witness source unavailable.'}
            </Box>
          </Box>
        )}
      </Box>
    </Box>
  );
}
