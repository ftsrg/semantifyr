/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Collapse from '@mui/material/Collapse';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import type { TraceEntry, CallTrace, CallTraceStep } from '../../lib/verification';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

const monoSx = {
  fontFamily: "'JetBrains Mono', monospace",
  fontSize: FONT_SIZE.md,
} as const;

interface TraceEntryNodeProps {
  call: TraceEntry;
  depth: number;
}

function TraceEntryNode({ call, depth }: TraceEntryNodeProps): React.JSX.Element {
  const [open, setOpen] = useState(true);
  const inner = call.innerTraces ?? [];
  const hasChildren = inner.length > 0;
  const args = call.arguments
    .map((a) => `${a.parameter}=${a.value}`)
    .join(', ');
  const headerText = `${call.self}.${call.calledTransition}(${args})`;
  return (
    <Box sx={{ pl: depth * 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        {hasChildren ? (
          <IconButton size="small" sx={{ p: 0, color: 'text.secondary' }} onClick={() => setOpen((prev) => !prev)}>
            {open ? <KeyboardArrowDownIcon sx={{ fontSize: ICON_SIZE.sm }} /> : <KeyboardArrowRightIcon sx={{ fontSize: ICON_SIZE.sm }} />}
          </IconButton>
        ) : (
          <Box sx={{ width: 18 }} />
        )}
        <Typography sx={{ ...monoSx, color: 'text.primary' }}>{headerText}</Typography>
      </Box>
      {hasChildren && (
        <Collapse in={open} timeout="auto">
          {inner.map((child, i) => (
            <TraceEntryNode key={`${child.self}.${child.calledTransition}.${i}`} call={child} depth={depth + 1} />
          ))}
        </Collapse>
      )}
    </Box>
  );
}

interface CallStepRowProps {
  step: CallTraceStep;
  label: string;
  defaultOpen: boolean;
}

function CallStepRow({ step, label, defaultOpen }: CallStepRowProps): React.JSX.Element {
  const [open, setOpen] = useState(defaultOpen);
  const callCount = step.traces.length;
  const summary = callCount === 0
    ? 'no transitions'
    : `${callCount} transition${callCount === 1 ? '' : 's'}`;
  return (
    <Box sx={{ borderBottom: '1px solid var(--surface-border)' }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          px: 1.5,
          py: 0.5,
          cursor: 'pointer',
          '&:hover': { bgcolor: 'var(--surface-bg)' },
        }}
        onClick={() => setOpen((prev) => !prev)}
      >
        <IconButton size="small" sx={{ p: 0, color: 'text.secondary' }}>
          {open ? <KeyboardArrowDownIcon sx={{ fontSize: ICON_SIZE.sm }} /> : <KeyboardArrowRightIcon sx={{ fontSize: ICON_SIZE.sm }} />}
        </IconButton>
        <Typography
          sx={{
            fontSize: FONT_SIZE.sm,
            color: 'text.primary',
            textTransform: 'uppercase',
            letterSpacing: '0.04em',
            fontWeight: 600,
            minWidth: 80,
          }}
        >
          {label}
        </Typography>
        <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}>{summary}</Typography>
      </Box>
      <Collapse in={open} timeout="auto">
        <Box sx={{ pl: 4.5, pr: 1.5, pb: 1, pt: 0.25 }}>
          {callCount === 0 ? (
            <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary', fontStyle: 'italic' }}>
              (no transitions)
            </Typography>
          ) : (
            step.traces.map((call, i) => (
              <TraceEntryNode key={i} call={call} depth={0} />
            ))
          )}
        </Box>
      </Collapse>
    </Box>
  );
}

interface Props {
  trace: CallTrace;
}

export default function CallTraceView({ trace }: Props): React.JSX.Element {
  // Open the Initial step by default; every numbered step defaults to closed so a long trace
  // doesn't drown the user on first paint. The user expands the steps that interest them.
  const rows: React.JSX.Element[] = [];
  rows.push(
    <CallStepRow
      key="initial"
      step={trace.initialStep}
      label="Initial"
      defaultOpen
    />,
  );
  trace.steps.forEach((step, i) => {
    rows.push(
      <CallStepRow
        key={`step-${i}`}
        step={step}
        label={`Step ${i + 1}`}
        defaultOpen={false}
      />,
    );
  });
  return <Box sx={{ overflowY: 'auto', height: '100%' }}>{rows}</Box>;
}
