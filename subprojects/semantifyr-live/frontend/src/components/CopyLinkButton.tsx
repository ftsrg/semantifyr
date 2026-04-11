/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined';

interface Props {
  onClick: () => void;
  confirmationMessage: string | null;
}

export default function CopyLinkButton({ onClick, confirmationMessage }: Props): React.JSX.Element {
  return (
    <Tooltip
      title={confirmationMessage ?? 'Copy link'}
      open={confirmationMessage !== null ? true : undefined}
    >
      <IconButton
        size="small"
        onClick={onClick}
        aria-label="Copy link"
        sx={{ color: 'var(--text)' }}
      >
        <LinkOutlinedIcon sx={{ fontSize: 24 }} />
      </IconButton>
    </Tooltip>
  );
}
