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
