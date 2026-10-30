/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Box from '@mui/material/Box';
import LinearProgress from '@mui/material/LinearProgress';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import { FONT_SIZE } from '../../lib/util/theme';

export interface StatusBarInfoItem {
  label: string;
  value: string;
  tooltip?: string | undefined;
}

interface Props {
  message: string | null;
  showProgress: boolean;
  infoItems?: readonly StatusBarInfoItem[] | undefined;
  onInfoClick?: (() => void) | undefined;
  trailing?: React.ReactNode;
}

export default function StatusBar({ message, showProgress, infoItems, onInfoClick, trailing }: Props): React.JSX.Element {
  return (
    <Box
      sx={{
        bgcolor: 'var(--surface-toolbar-bg)',
        borderTop: '1px solid var(--surface-border)',
      }}
    >
      <LinearProgress
        sx={{
          height: 2,
          '& .MuiLinearProgress-bar': { bgcolor: 'var(--accent)' },
          bgcolor: 'var(--surface-border)',
          visibility: showProgress ? 'visible' : 'hidden',
        }}
      />
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          px: 2,
          py: 0.25,
          gap: 1,
          minHeight: 24,
        }}
      >
        <Typography
          variant="body2"
          sx={{
            flex: 1,
            fontSize: FONT_SIZE.sm,
            color: 'text.secondary',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {message ?? '\u00A0'}
        </Typography>

        {infoItems && infoItems.length > 0 && (
          <>
            <Divider
              orientation="vertical"
              flexItem
              sx={{ my: 0.25, display: { xs: 'none', sm: 'block' } }}
            />
            <Box
              onClick={onInfoClick}
              sx={{
                display: { xs: 'none', sm: 'flex' },
                alignItems: 'center',
                gap: 1,
                cursor: onInfoClick ? 'pointer' : 'default',
                '&:hover': onInfoClick ? { opacity: 0.8 } : {},
              }}
            >
              {infoItems.map((item) => (
                <Tooltip key={item.label} title={item.tooltip ?? item.label}>
                  <Typography
                    variant="caption"
                    sx={{
                      fontSize: FONT_SIZE.xs,
                      color: 'text.secondary',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {item.value}
                  </Typography>
                </Tooltip>
              ))}
            </Box>
          </>
        )}
        {trailing && (
          <>
            <Divider orientation="vertical" flexItem sx={{ my: 0.25 }} />
            {trailing}
          </>
        )}
      </Box>
    </Box>
  );
}
