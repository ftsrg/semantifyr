/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import ContrastOutlinedIcon from '@mui/icons-material/ContrastOutlined';
import type { ColorModePreference } from '../lib/theme';

interface Props {
  preference: ColorModePreference;
  onToggle: () => void;
}

const LABELS: Record<ColorModePreference, string> = {
  system: 'Switch to light mode',
  light: 'Switch to dark mode',
  dark: 'Switch to system theme',
};

const ICONS: Record<ColorModePreference, React.ElementType> = {
  light: LightModeOutlinedIcon,
  dark: DarkModeOutlinedIcon,
  system: ContrastOutlinedIcon,
};

export default function ColorModeToggle({ preference, onToggle }: Props): React.JSX.Element {
  const Icon = ICONS[preference];
  return (
    <Tooltip title={LABELS[preference]}>
      <IconButton
        size="small"
        onClick={onToggle}
        aria-label={LABELS[preference]}
        sx={{ color: 'var(--text)' }}
      >
        <Icon sx={{ fontSize: 24 }} />
      </IconButton>
    </Tooltip>
  );
}
