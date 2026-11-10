/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import { FONT_SIZE } from '../../lib/util/theme';

export default function EditorLoading({ message }: { message: string }): React.JSX.Element {
  return (
    <Box sx={{ height: '100dvh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2, bgcolor: 'var(--page-bg)' }}>
      <CircularProgress size={32} sx={{ color: 'text.secondary' }} />
      <Typography sx={{ color: 'text.secondary', fontSize: FONT_SIZE.md }}>
        {message}
      </Typography>
    </Box>
  );
}
