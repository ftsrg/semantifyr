/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import CircularProgress from '@mui/material/CircularProgress';
import Box from '@mui/material/Box';
import CloudIcon from '@mui/icons-material/Cloud';
import CloudOffOutlinedIcon from '@mui/icons-material/CloudOffOutlined';
import type { LiveEditorStatus } from './LiveEditor';

interface Props {
  status: LiveEditorStatus;
  onReconnect: () => void;
  onDisconnect: () => void;
}

export default function ConnectionStatus({ status, onReconnect, onDisconnect }: Props): React.JSX.Element {
  if (status === 'initializing') {
    return (
      <Tooltip title="Initializing">
        <Box sx={{ display: 'inline-flex', alignItems: 'center', px: 0.5 }}>
          <CircularProgress size={22} sx={{ color: 'var(--text-muted)' }} />
        </Box>
      </Tooltip>
    );
  }

  if (status === 'reconnecting') {
    return (
      <Tooltip title="Reconnecting">
        <Box sx={{ display: 'inline-flex', alignItems: 'center', px: 0.5 }}>
          <CircularProgress size={22} sx={{ color: 'var(--text-muted)' }} />
        </Box>
      </Tooltip>
    );
  }

  if (status === 'connected') {
    return (
      <Tooltip title="Connected - click to disconnect">
        <IconButton size="small" onClick={onDisconnect} sx={{ color: 'var(--text)' }}>
          <CloudIcon sx={{ fontSize: 24 }} />
        </IconButton>
      </Tooltip>
    );
  }

  if (status === 'errored') {
    return (
      <Tooltip title="Connection error - click to reconnect">
        <IconButton size="small" onClick={onReconnect} sx={{ color: 'var(--danger)' }}>
          <CloudOffOutlinedIcon sx={{ fontSize: 24 }} />
        </IconButton>
      </Tooltip>
    );
  }

  return (
    <Tooltip title="Disconnected - click to connect">
      <IconButton size="small" onClick={onReconnect} sx={{ color: 'var(--text-muted)', opacity: 0.5 }}>
        <CloudOffOutlinedIcon sx={{ fontSize: 24 }} />
      </IconButton>
    </Tooltip>
  );
}
