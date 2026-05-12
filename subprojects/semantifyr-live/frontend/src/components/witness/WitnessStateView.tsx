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
import type { WitnessState, WitnessStateStep, WitnessStateValue } from '../../lib/verification';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

const monoSx = {
  fontFamily: "'JetBrains Mono', monospace",
  fontSize: FONT_SIZE.md,
} as const;

interface ChangedBinding {
  variable: string;
  value: string;
  previous: string | undefined;
}

function diffBindings(step: WitnessStateStep, previousValues: Map<string, string>): ChangedBinding[] {
  const changed: ChangedBinding[] = [];
  for (const v of step.values) {
    const prev = previousValues.get(v.variable);
    if (prev !== v.value) {
      changed.push({ variable: v.variable, value: v.value, previous: prev });
    }
  }
  return changed;
}

function valuesAfter(step: WitnessStateStep, base: Map<string, string>): Map<string, string> {
  const next = new Map(base);
  for (const v of step.values) {
    next.set(v.variable, v.value);
  }
  return next;
}

interface StateStepRowProps {
  label: string;
  initial: boolean;
  changed: ChangedBinding[];
  allValues: WitnessStateValue[];
}

function StateStepRow({ label, initial, changed, allValues }: StateStepRowProps): React.JSX.Element {
  // Always default closed: even the Initial step typically lists every variable in the model,
  // which would crowd the panel on first paint. The user opens the rows they care about.
  const [open, setOpen] = useState(false);
  const changeCount = changed.length;
  const changeBadge = initial
    ? `${allValues.length} variable${allValues.length === 1 ? '' : 's'}`
    : changeCount === 0
      ? 'no observable change'
      : `${changeCount} change${changeCount === 1 ? '' : 's'}`;
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
        <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}>{changeBadge}</Typography>
      </Box>
      <Collapse in={open} timeout="auto">
        <Box sx={{ pl: 4.5, pr: 1.5, pb: 1 }}>
          {allValues.length > 0 ? (
            <Box sx={{ mt: 0.25 }}>
              {allValues.map((v) => {
                const change = changed.find((c) => c.variable === v.variable);
                const isChanged = change !== undefined;
                const color = isChanged ? 'var(--accent)' : 'var(--text-muted)';
                return (
                  <Typography key={v.variable} sx={{ ...monoSx, color }}>
                    {v.variable} = {v.value}
                    {isChanged && change.previous !== undefined && (
                      <Typography component="span" sx={{ ...monoSx, color: 'text.secondary', ml: 1 }}>
                        (was {change.previous})
                      </Typography>
                    )}
                  </Typography>
                );
              })}
            </Box>
          ) : (
            <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary', mt: 0.5, fontStyle: 'italic' }}>
              (empty)
            </Typography>
          )}
        </Box>
      </Collapse>
    </Box>
  );
}

interface Props {
  trace: WitnessState;
}

export default function WitnessStateView({ trace }: Props): React.JSX.Element {
  const rows: React.JSX.Element[] = [];
  let previousValues = new Map<string, string>();

  const initialChanged = trace.initialStep.values.map((v) => ({
    variable: v.variable,
    value: v.value,
    previous: undefined,
  }));
  rows.push(
    <StateStepRow
      key="initial"
      label="Initial"
      initial
      changed={initialChanged}
      allValues={trace.initialStep.values}
    />,
  );
  previousValues = valuesAfter(trace.initialStep, previousValues);

  trace.steps.forEach((step, i) => {
    const changed = diffBindings(step, previousValues);
    rows.push(
      <StateStepRow
        key={`step-${i}`}
        label={`Step ${i + 1}`}
        initial={false}
        changed={changed}
        allValues={step.values}
      />,
    );
    previousValues = valuesAfter(step, previousValues);
  });

  return <Box sx={{ overflowY: 'auto', height: '100%' }}>{rows}</Box>;
}
