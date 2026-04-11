/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import RefreshOutlinedIcon from '@mui/icons-material/RefreshOutlined';

interface Props {
  disabled: boolean;
  onClick: (event: React.MouseEvent) => void;
}

export default function RefreshButton({ disabled, onClick }: Props): React.JSX.Element {
  return (
    <Tooltip title="Refresh cases">
      <span>
        <IconButton
          size="small"
          onClick={onClick}
          disabled={disabled}
          aria-label="Refresh cases"
          sx={{ color: 'var(--text-muted)' }}
        >
          <RefreshOutlinedIcon sx={{ fontSize: 20 }} />
        </IconButton>
      </span>
    </Tooltip>
  );
}
