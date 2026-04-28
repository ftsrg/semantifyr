/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

interface Props {
  oxstsSource: string | null;
  lastUpdated: number | null;
}

export default function OxstsPreviewPane({ oxstsSource, lastUpdated }: Props): React.JSX.Element {
  return (
    <Box
      sx={{
        flex: '1 1 0',
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        borderLeft: '1px solid var(--surface-border)',
        bgcolor: 'var(--page-bg)',
      }}
    >
      <Box
        sx={{
          px: 1.5,
          py: 0.5,
          borderBottom: '1px solid var(--surface-border)',
          bgcolor: 'var(--surface-toolbar-bg)',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <Typography sx={{ fontSize: '0.85rem', color: 'var(--text)', fontWeight: 500 }}>
          Generated Oxsts
        </Typography>
        {lastUpdated !== null && (
          <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            updated {new Date(lastUpdated).toLocaleTimeString()}
          </Typography>
        )}
      </Box>
      <Box
        component="pre"
        sx={{
          flex: '1 1 auto',
          m: 0,
          p: 1.5,
          overflow: 'auto',
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: '0.85rem',
          color: 'var(--text)',
          whiteSpace: 'pre',
        }}
      >
        {oxstsSource ?? '// Run a verification to see the compiled Oxsts model here.'}
      </Box>
    </Box>
  );
}
