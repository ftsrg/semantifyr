/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import PlaylistPlayIcon from '@mui/icons-material/PlaylistPlay';
import StopIcon from '@mui/icons-material/Stop';

interface Props {
  busy: boolean;
  disabled: boolean;
  onVerify: (event: React.MouseEvent) => void;
  onCancel: (event: React.MouseEvent) => void;
}

const ICON_BOX_SIZE = 20;

export default function VerifyButton({ busy, disabled, onVerify, onCancel }: Props): React.JSX.Element {
  return (
    <Tooltip title={busy ? 'Cancel verification' : 'Verify all cases'}>
      <span>
        <IconButton
          size="small"
          onClick={busy ? onCancel : onVerify}
          disabled={!busy && disabled}
          aria-label={busy ? 'Cancel verification' : 'Run verification'}
          sx={{ color: 'var(--accent)' }}
        >
          <Box sx={{ width: ICON_BOX_SIZE, height: ICON_BOX_SIZE, display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
            {busy && <CircularProgress size={16} sx={{ color: 'var(--accent)', position: 'absolute' }} />}
            {busy
              ? <StopIcon sx={{ fontSize: 14 }} />
              : <PlaylistPlayIcon sx={{ fontSize: ICON_BOX_SIZE }} />
            }
          </Box>
        </IconButton>
      </span>
    </Tooltip>
  );
}
