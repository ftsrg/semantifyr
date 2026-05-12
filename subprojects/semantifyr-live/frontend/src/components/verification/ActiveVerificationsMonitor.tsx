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
  /** Click handler. The status-bar version opens the side-panel's Verifications tab. */
  onActivate: () => void;
}

/**
 * Compact status-bar indicator for the current session's active verifications and
 * validations. Just a badge + click handler - the parent decides what to do (typically open
 * the side panel's Verifications tab where the same data is shown in full). Shares its
 * subscription with {@link RunningVerificationsTab} via {@link useActiveVerifications} so both
 * surfaces show the same authoritative server-side state.
 */
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
