/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import ClearIcon from '@mui/icons-material/Clear';
import SearchIcon from '@mui/icons-material/Search';
import type { SessionInfo } from '../../lib/api';
import SessionsTable from './SessionsTable';
import SessionsCardList from './SessionsCardList';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  sessions: readonly SessionInfo[];
  totalSessions: number;
  filter: string;
  onFilterChange: (value: string) => void;
  onCancelSession: (sessionId: string) => void;
  onCancelVerification: (verificationId: string) => void;
}

export default function SessionsTab({
  sessions,
  totalSessions,
  filter,
  onFilterChange,
  onCancelSession,
  onCancelVerification,
}: Props): React.JSX.Element {
  const theme = useTheme();
  const compact = useMediaQuery(theme.breakpoints.down('md'));

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      <SessionsHeader
        total={totalSessions}
        shown={sessions.length}
        filter={filter}
        onFilterChange={onFilterChange}
      />
      {compact ? (
        <SessionsCardList
          sessions={sessions}
          onCancelSession={onCancelSession}
          onCancelVerification={onCancelVerification}
        />
      ) : (
        <SessionsTable
          sessions={sessions}
          onCancelSession={onCancelSession}
          onCancelVerification={onCancelVerification}
        />
      )}
    </Box>
  );
}

interface SessionsHeaderProps {
  total: number;
  shown: number;
  filter: string;
  onFilterChange: (value: string) => void;
}

function SessionsHeader({ total, shown, filter, onFilterChange }: SessionsHeaderProps): React.JSX.Element {
  const filtered = filter.trim().length > 0;
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 0.5, flexWrap: 'wrap' }}>
      <Typography sx={{ fontSize: FONT_SIZE.lg, fontWeight: 600 }}>Sessions</Typography>
      <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>
        {total === 0
          ? 'none active'
          : filtered
            ? `${shown} of ${total} shown`
            : `${total} active`}
      </Typography>
      <Box sx={{ flex: 1, minWidth: 8 }} />
      <TextField
        size="small"
        placeholder="Filter by session, IP, or flavor"
        value={filter}
        onChange={(e) => onFilterChange(e.target.value)}
        sx={{
          width: { xs: '100%', sm: 280 },
          '& .MuiInputBase-root': { color: 'text.primary', bgcolor: 'var(--surface-bg)', fontSize: FONT_SIZE.sm },
          '& .MuiOutlinedInput-notchedOutline': { borderColor: 'var(--surface-border)' },
        }}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon sx={{ fontSize: ICON_SIZE.sm, color: 'text.secondary' }} />
              </InputAdornment>
            ),
            endAdornment: filter ? (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => onFilterChange('')} sx={{ color: 'text.secondary' }}>
                  <ClearIcon sx={{ fontSize: ICON_SIZE.sm }} />
                </IconButton>
              </InputAdornment>
            ) : undefined,
          },
        }}
      />
    </Box>
  );
}
