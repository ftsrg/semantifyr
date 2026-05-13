/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Badge from '@mui/material/Badge';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import HourglassEmptyOutlinedIcon from '@mui/icons-material/HourglassEmptyOutlined';
import { useActiveVerifications } from '../../lib/hooks/useActiveVerifications';
import type { SemantifyrLiveApi } from '../../lib/api/lspExtensions';
import { ICON_SIZE } from '../../lib/util/theme';

interface Props {
  api: SemantifyrLiveApi | null;
  connected: boolean;
  onActivate: () => void;
}

export default function ActiveVerificationsMonitor({ api, connected, onActivate }: Props): React.JSX.Element {
  const { items } = useActiveVerifications(api, connected);
  const count = items.length;
  const tooltip = count === 0
    ? 'No running verifications or validations - click to open Verifications panel'
    : `${count} job${count === 1 ? '' : 's'} running - click to open Verifications panel`;
  return (
    <Tooltip title={tooltip}>
      <IconButton
        size="small"
        onClick={onActivate}
        aria-label="Open Verifications panel"
        sx={{ color: count > 0 ? 'var(--warning)' : 'var(--text-muted)', p: 0.25 }}
      >
        <Badge badgeContent={count} color="warning" overlap="circular" invisible={count === 0}>
          <HourglassEmptyOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />
        </Badge>
      </IconButton>
    </Tooltip>
  );
}
