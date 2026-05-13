/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useState, type JSX } from 'react';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined';
import { ICON_SIZE } from '../../lib/util/theme';

interface Props {
  onClick: () => void;
  confirmationMessage: string | null;
}

export default function CopyLinkButton({ onClick, confirmationMessage }: Props): JSX.Element {
  const [hovered, setHovered] = useState(false);
  const forced = confirmationMessage !== null;
  return (
    <Tooltip
      title={confirmationMessage ?? 'Copy link'}
      open={forced || hovered}
      onOpen={() => { setHovered(true); }}
      onClose={() => { setHovered(false); }}
    >
      <IconButton
        size="small"
        onClick={onClick}
        aria-label="Copy link"
        sx={{ color: 'text.primary' }}
      >
        <LinkOutlinedIcon sx={{ fontSize: ICON_SIZE.xl }} />
      </IconButton>
    </Tooltip>
  );
}
