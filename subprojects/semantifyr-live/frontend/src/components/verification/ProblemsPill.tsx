/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Popover from '@mui/material/Popover';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmberOutlined';
import type { LiveEditorHandle, ProblemEntry } from '../editor/LiveEditor';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  editorHandle: LiveEditorHandle | null;
}

export default function ProblemsPill({ editorHandle }: Props): React.JSX.Element {
  const [problems, setProblems] = useState<ProblemEntry[]>([]);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  useEffect(() => {
    if (!editorHandle) return;
    const refresh = () => { setProblems(editorHandle.getProblems()); };
    refresh();
    const dispose = editorHandle.addProblemsListener(refresh);
    return () => { dispose(); };
  }, [editorHandle]);

  const errors = problems.filter((p) => p.severity === 'error').length;
  const warnings = problems.filter((p) => p.severity === 'warning').length;
  const total = errors + warnings;
  if (total === 0) return <></>;

  const tooltip = warnings === 0
    ? `${errors} error${errors === 1 ? '' : 's'}`
    : errors === 0
      ? `${warnings} warning${warnings === 1 ? '' : 's'}`
      : `${errors} error${errors === 1 ? '' : 's'}, ${warnings} warning${warnings === 1 ? '' : 's'}`;

  return (
    <>
      <Tooltip title={`${tooltip} - click for details`}>
        <Chip
          label={
            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.4 }}>
              {errors > 0 && <ErrorOutlineIcon sx={{ fontSize: ICON_SIZE.sm }} />}
              {errors > 0 && <span>{errors}</span>}
              {warnings > 0 && <WarningAmberIcon sx={{ fontSize: ICON_SIZE.sm }} />}
              {warnings > 0 && <span>{warnings}</span>}
            </Box>
          }
          size="small"
          onClick={(event) => { setAnchor(event.currentTarget); }}
          sx={{
            ml: 0.5,
            bgcolor: errors > 0 ? 'var(--danger-soft-bg)' : 'var(--warning-soft-bg)',
            color: errors > 0 ? 'var(--danger)' : 'var(--warning)',
            fontWeight: 600,
            fontSize: FONT_SIZE.xs,
            height: 20,
            cursor: 'pointer',
            '&:hover': { opacity: 0.85 },
          }}
        />
      </Tooltip>
      <Popover
        anchorEl={anchor}
        open={anchor !== null}
        onClose={() => { setAnchor(null); }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        slotProps={{
          paper: {
            sx: {
              minWidth: 360,
              maxWidth: 560,
              maxHeight: 320,
              overflowY: 'auto',
            },
          },
        }}
      >
        {problems.map((p, idx) => (
          <Box
            key={`${p.startLine}-${p.startColumn}-${p.message}-${idx}`}
            onClick={() => {
              setAnchor(null);
              editorHandle?.revealProblem(p);
            }}
            sx={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 0.75,
              px: 1.5,
              py: 0.6,
              cursor: 'pointer',
              borderBottom: '1px solid var(--surface-border)',
              '&:last-child': { borderBottom: 'none' },
              '&:hover': { bgcolor: 'var(--surface-panel-bg)' },
            }}
          >
            <Box sx={{ pt: '2px' }}>
              {p.severity === 'error' ? (
                <ErrorOutlineIcon sx={{ fontSize: ICON_SIZE.sm, color: 'var(--danger)' }} />
              ) : p.severity === 'warning' ? (
                <WarningAmberIcon sx={{ fontSize: ICON_SIZE.sm, color: 'var(--warning)' }} />
              ) : (
                <ErrorOutlineIcon sx={{ fontSize: ICON_SIZE.sm, color: 'text.secondary' }} />
              )}
            </Box>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography sx={{ fontSize: FONT_SIZE.sm, whiteSpace: 'pre-wrap' }}>
                {p.message}
              </Typography>
              <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}>
                {p.source ? `${p.source} · ` : ''}line {p.startLine + 1}:{p.startColumn + 1}
              </Typography>
            </Box>
          </Box>
        ))}
      </Popover>
    </>
  );
}

