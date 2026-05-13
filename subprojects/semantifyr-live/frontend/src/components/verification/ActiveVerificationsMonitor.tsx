/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
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
  const running = items.filter((v) => v.state === 'Running').length;
  const queued = items.filter((v) => v.state === 'Queued').length;
  const total = items.length;
  const tooltip = total === 0
    ? 'No running verifications or validations - click to open Verifications panel'
    : queued > 0
      ? `${running} running, ${queued} queued - click to open Verifications panel`
      : `${running} running - click to open Verifications panel`;
  return (
    <Tooltip title={tooltip}>
      <IconButton
        size="small"
        onClick={onActivate}
        aria-label="Open Verifications panel"
        sx={{ color: total > 0 ? 'var(--warning)' : 'var(--text-muted)', p: 0.25 }}
      >
        <Badge
          badgeContent={queued > 0 ? `${running}+${queued}` : running}
          color="warning"
          overlap="circular"
          invisible={total === 0}
        >
          <HourglassEmptyOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />
        </Badge>
      </IconButton>
    </Tooltip>
  );
}
