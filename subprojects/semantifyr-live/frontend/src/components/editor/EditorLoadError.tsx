/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import CloudOffOutlinedIcon from '@mui/icons-material/CloudOffOutlined';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

/**
 * Shown in place of the editor when the editor bundle itself never made it down from the
 * server: the lazy {@code import('./editor/LiveEditor')} rejected (offline on first visit,
 * a stale chunk reference after a deploy, a network blip mid-load). The whole Monaco stack
 * is missing at this point, so a reload is the only sensible recovery; the service worker
 * makes that reload cheap on a repeat visit.
 */
export default function EditorLoadError(): React.JSX.Element {
  const online = typeof navigator === 'undefined' || navigator.onLine !== false;
  return (
    <Box
      role="alert"
      sx={{
        flex: '1 1 auto',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 1.5,
        p: 3,
        textAlign: 'center',
        bgcolor: 'var(--page-bg)',
        color: 'text.primary',
      }}
    >
      <CloudOffOutlinedIcon sx={{ fontSize: ICON_SIZE.xxl, color: 'text.secondary' }} />
      <Typography sx={{ fontSize: FONT_SIZE.xl, fontWeight: 600 }}>
        Could not load the editor
      </Typography>
      <Typography sx={{ fontSize: FONT_SIZE.md, color: 'text.secondary', maxWidth: 420 }}>
        {online
          ? 'The editor failed to download. This is usually a transient network issue; reloading the page should fix it.'
          : "You appear to be offline, so the editor could not be downloaded. Reconnect and reload the page."}
      </Typography>
      <Button
        size="small"
        variant="contained"
        color="primary"
        onClick={() => window.location.reload()}
        sx={{ mt: 0.5, fontSize: FONT_SIZE.md }}
      >
        Reload page
      </Button>
    </Box>
  );
}
