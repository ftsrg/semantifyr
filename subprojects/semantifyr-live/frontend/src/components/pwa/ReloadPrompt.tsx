/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useState } from 'react';
import { useRegisterSW } from 'virtual:pwa-register/react';
import Snackbar from '@mui/material/Snackbar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';
import { isPageOutdated } from '../../lib/util/outdated';

const UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000;

export default function ReloadPrompt(): React.JSX.Element {
  const [outdated, setOutdated] = useState(false);
  useRegisterSW({
    // a new worker also takes over a page that already came from the network after the deploy
    onNeedReload: () => {
      isPageOutdated().then(setOutdated, (error: unknown) => {
        console.warn('semantifyr-live: could not check for a new version', error);
      });
    },
    onRegisteredSW: (_url, registration) => {
      if (registration) {
        setInterval(() => {
          void registration.update();
        }, UPDATE_CHECK_INTERVAL_MS);
      }
    },
    onRegisterError: (error) => {
      console.warn('semantifyr-live: service worker registration failed', error);
    },
  });

  return (
    <Snackbar open={outdated} anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}>
      <Box
        role="status"
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          pl: 2,
          pr: 0.75,
          py: 0.75,
          borderRadius: 1,
          bgcolor: 'var(--surface-bg)',
          color: 'text.primary',
          border: '1px solid var(--surface-border)',
          borderLeft: '3px solid var(--accent)',
          boxShadow: '0 2px 12px rgba(0, 0, 0, 0.3)',
          fontSize: FONT_SIZE.md,
        }}
      >
        <Box component="span" sx={{ mr: 0.5 }}>A new version is available.</Box>
        <Button
          size="small"
          color="primary"
          onClick={() => { window.location.reload(); }}
          sx={{ fontSize: FONT_SIZE.sm }}
        >
          Reload
        </Button>
        <IconButton
          size="small"
          aria-label="Dismiss"
          onClick={() => { setOutdated(false); }}
          sx={{ color: 'text.secondary', p: 0.25, '&:hover': { color: 'text.primary' } }}
        >
          <CloseIcon sx={{ fontSize: ICON_SIZE.sm }} />
        </IconButton>
      </Box>
    </Snackbar>
  );
}
