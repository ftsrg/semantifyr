/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import CloseIcon from '@mui/icons-material/Close';
import CloudOffOutlinedIcon from '@mui/icons-material/CloudOffOutlined';
import WifiOffOutlinedIcon from '@mui/icons-material/WifiOffOutlined';

import type { LiveEditorStatus } from '../editor/LiveEditor';
import { useOnlineStatus } from '../../lib/hooks';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  status: LiveEditorStatus;
  statusInfo: string | null;
  onReconnect: () => void;
}

interface BannerContent {
  icon: React.ReactNode;
  title: string;
  body: string;
  accent: string;
  actionLabel: string;
}

export default function ConnectionBanner({ status, statusInfo, onReconnect }: Props): React.JSX.Element | null {
  const online = useOnlineStatus();
  const [dismissed, setDismissed] = useState(false);

  // A fresh errored/disconnected transition re-opens the chip after the user dismissed it.
  useEffect(() => {
    setDismissed(false);
  }, [status]);

  if ((status !== 'errored' && status !== 'disconnected') || dismissed) {
    return null;
  }

  const content: BannerContent = status === 'disconnected'
    ? {
        icon: <CloudOffOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />,
        title: 'Disconnected',
        body: 'Diagnostics and verification are paused.',
        accent: 'var(--text-muted)',
        actionLabel: 'Reconnect',
      }
    : !online
      ? {
          icon: <WifiOffOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />,
          title: "You're offline",
          body: 'Editing works; live features resume when you reconnect.',
          accent: 'var(--warning)',
          actionLabel: 'Retry',
        }
      : {
          icon: <CloudOffOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />,
          title: 'Connection lost',
          body: statusInfo ?? 'Could not reach the language server.',
          accent: 'var(--danger)',
          actionLabel: 'Retry',
        };

  return (
    <Box
      role={status === 'errored' ? 'alert' : 'status'}
      sx={{
        position: 'absolute',
        bottom: 12,
        right: 16,
        zIndex: 6,
        maxWidth: 'min(340px, calc(100% - 32px))',
        display: 'flex',
        alignItems: 'flex-start',
        gap: 1,
        pl: 1.25,
        pr: 0.5,
        py: 0.75,
        borderRadius: 1,
        bgcolor: 'var(--surface-bg)',
        border: '1px solid var(--surface-border)',
        borderLeft: `3px solid ${content.accent}`,
        boxShadow: '0 2px 10px rgba(0, 0, 0, 0.25)',
        color: 'text.primary',
      }}
    >
      <Box sx={{ color: content.accent, display: 'flex', alignItems: 'center', pt: '2px' }}>
        {content.icon}
      </Box>
      <Box sx={{ flex: 1, minWidth: 0, pt: '1px' }}>
        <Typography sx={{ fontSize: FONT_SIZE.sm, fontWeight: 600, lineHeight: 1.3 }}>
          {content.title}
        </Typography>
        <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary', mt: 0.25, lineHeight: 1.3 }}>
          {content.body}
        </Typography>
        <Button
          size="small"
          color="primary"
          onClick={onReconnect}
          sx={{ mt: 0.5, px: 1, py: 0.1, minWidth: 0, fontSize: FONT_SIZE.xs }}
        >
          {content.actionLabel}
        </Button>
      </Box>
      <IconButton
        size="small"
        aria-label="Dismiss"
        onClick={() => setDismissed(true)}
        sx={{ color: 'text.secondary', p: 0.25, '&:hover': { color: 'text.primary' } }}
      >
        <CloseIcon sx={{ fontSize: ICON_SIZE.sm }} />
      </IconButton>
    </Box>
  );
}
