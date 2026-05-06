/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useCallback, useEffect, useState } from 'react';
import Badge from '@mui/material/Badge';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Popover from '@mui/material/Popover';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import HourglassEmptyOutlinedIcon from '@mui/icons-material/HourglassEmptyOutlined';
import StopIcon from '@mui/icons-material/Stop';
import type { ActiveVerificationInfo } from '../lib/adminApi';
import { formatIsoDuration } from '../lib/duration';
import type { LiveEditorHandle } from './LiveEditor';

interface Props {
  editorHandle: LiveEditorHandle | null;
  connected: boolean;
}

interface InflightChangedParams {
  inflight?: ActiveVerificationInfo[];
}

/**
 * Bridge-side monitor for the current session's in-flight verifications and validations.
 *
 * <p>Speaks the {@code semantifyr/live/inflight/*} JSON-RPC methods that the live-server
 * intercepts and never forwards to the LSP child. Subscribes to
 * {@code semantifyr/live/inflight/changed} notifications to track state without polling.
 */
export default function InflightMonitor({ editorHandle, connected }: Props): React.JSX.Element {
  const [items, setItems] = useState<ActiveVerificationInfo[]>([]);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  const refresh = useCallback(async () => {
    if (!connected) return;
    const client = editorHandle?.getLspClient();
    if (!client) return;
    try {
      const result = (await client.sendRequest('semantifyr/live/inflight/list')) as InflightChangedParams | null;
      setItems(result?.inflight ?? []);
    } catch {
      /* the request is intercepted before the LSP server sees it; transient connection drops fall through */
    }
  }, [editorHandle, connected]);

  useEffect(() => {
    if (!connected || !editorHandle) return;
    void refresh();
    const dispose = editorHandle.addNotificationListener('semantifyr/live/inflight/changed', (params: unknown) => {
      const p = params as InflightChangedParams | undefined;
      setItems(p?.inflight ?? []);
    });
    return () => { dispose?.(); };
  }, [editorHandle, connected, refresh]);

  const handleCancel = useCallback(async (requestId: string) => {
    const client = editorHandle?.getLspClient();
    if (!client) return;
    try {
      await client.sendRequest('semantifyr/live/inflight/cancel', { requestId });
    } catch {
      /* ignore - the changed notification will refresh us */
    }
  }, [editorHandle]);

  const handleCancelAll = useCallback(async () => {
    const client = editorHandle?.getLspClient();
    if (!client) return;
    try {
      await client.sendRequest('semantifyr/live/inflight/cancelAll');
    } catch {
      /* ignore - the changed notification will refresh us */
    }
  }, [editorHandle]);

  const count = items.length;
  return (
    <>
      <Tooltip title={count === 0 ? 'No in-flight jobs' : `${count} job${count === 1 ? '' : 's'} in flight`}>
        <IconButton
          size="small"
          onClick={(event) => setAnchor(event.currentTarget)}
          sx={{ color: count > 0 ? 'var(--warning)' : 'var(--text-muted)', p: 0.25 }}
        >
          <Badge badgeContent={count} color="warning" overlap="circular" invisible={count === 0}>
            <HourglassEmptyOutlinedIcon sx={{ fontSize: 18 }} />
          </Badge>
        </IconButton>
      </Tooltip>
      <Popover
        anchorEl={anchor}
        open={anchor !== null}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        slotProps={{
          paper: {
            sx: {
              bgcolor: 'var(--surface-bg)',
              color: 'var(--text)',
              border: '1px solid var(--surface-border)',
              minWidth: 360,
              maxWidth: 480,
            },
          },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 0.75 }}>
          <Typography sx={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)' }}>
            In-flight ({count})
          </Typography>
          <Box sx={{ flex: 1 }} />
          <Button
            size="small"
            disabled={count === 0}
            onClick={() => { void handleCancelAll(); }}
            sx={{ textTransform: 'none', fontSize: '0.72rem', color: 'var(--danger)' }}
          >
            Cancel all
          </Button>
        </Box>
        <Divider sx={{ borderColor: 'var(--surface-border)' }} />
        {count === 0 ? (
          <Box sx={{ px: 1.5, py: 1.5 }}>
            <Typography sx={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
              No in-flight verifications or validations.
            </Typography>
          </Box>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', maxHeight: 280, overflowY: 'auto' }}>
            {items.map((item) => (
              <Box
                key={item.requestId}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.75,
                  px: 1.5,
                  py: 0.5,
                  borderBottom: '1px solid var(--surface-border)',
                  '&:last-child': { borderBottom: 'none' },
                }}
              >
                <Chip
                  label={item.kind === 'Validate' ? 'validate' : 'verify'}
                  size="small"
                  sx={{
                    bgcolor: item.kind === 'Validate' ? 'rgba(96,165,250,0.18)' : 'rgba(251,191,36,0.15)',
                    color: item.kind === 'Validate' ? 'var(--accent)' : 'var(--warning)',
                    fontWeight: 600,
                    fontSize: '0.65rem',
                    height: 18,
                  }}
                />
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ fontSize: '0.78rem', color: 'var(--text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {item.caseLabel ?? `#${item.requestId}`}
                  </Typography>
                  <Typography sx={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                    {item.portfolioId ?? 'default'}
                    {item.elapsed ? ` · ${formatIsoDuration(item.elapsed)}` : ''}
                  </Typography>
                </Box>
                <Tooltip title="Cancel">
                  <IconButton size="small" onClick={() => { void handleCancel(item.requestId); }} sx={{ color: 'var(--danger)', p: 0.25 }}>
                    <StopIcon sx={{ fontSize: 16 }} />
                  </IconButton>
                </Tooltip>
              </Box>
            ))}
          </Box>
        )}
      </Popover>
    </>
  );
}
