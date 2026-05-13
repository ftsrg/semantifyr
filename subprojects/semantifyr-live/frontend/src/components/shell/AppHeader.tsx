/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import AppBar from '@mui/material/AppBar';
import MuiToolbar from '@mui/material/Toolbar';
import Box from '@mui/material/Box';

interface Props {
  logoSrc: string;
  children: React.ReactNode;
}

export default function AppHeader({ logoSrc, children }: Props): React.JSX.Element {
  return (
    <AppBar position="static">
      <MuiToolbar
        variant="dense"
        disableGutters
        sx={{
          px: { xs: 1, sm: 2 },
          gap: { xs: 0.5, sm: 1 },
          minHeight: { xs: 44, sm: 48 },
        }}
      >
        <a href="/" aria-label="Semantifyr Live" style={{ display: 'inline-flex', alignItems: 'center', textDecoration: 'none' }}>
          <Box
            component="img"
            src={logoSrc}
            alt="Semantifyr"
            sx={{ height: { xs: '1.3rem', sm: '1.75rem' }, width: 'auto', display: 'block' }}
          />
        </a>
        {children}
      </MuiToolbar>
    </AppBar>
  );
}
