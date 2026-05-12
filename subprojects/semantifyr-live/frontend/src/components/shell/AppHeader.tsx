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
  /** Theme-appropriate logo SVG path (`logo-full-light.svg` / `logo-full-dark.svg`). */
  logoSrc: string;
  /** Everything to the right of the logo: the toolbar controls / admin status row. */
  children: React.ReactNode;
}

/**
 * The shared top-bar chrome: a static AppBar (theme-styled) with a dense toolbar that opens
 * with the home-linked Semantifyr logo. Both the editor's `Toolbar` and the admin
 * `AdminHeader` are this shell plus their own content, so the two surfaces read as one app.
 */
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
