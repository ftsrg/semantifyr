/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createTheme, type Theme } from '@mui/material/styles';
import type { ResolvedColorMode } from './colorMode';

export const ICON_SIZE = {
  sm: 16,
  md: 18,
  lg: 20,
  xl: 24,
  xxl: 40,
} as const;

export const FONT_SIZE = {
  xs: '0.72rem',
  sm: '0.78rem',
  md: '0.85rem',
  lg: '0.95rem',
  xl: '1rem',
} as const;

// Keep in sync with the :root[data-theme=...] blocks in styles.css. MUI's palette needs real
// hex (it does lighten/darken/alpha maths) so it cannot read var().
const PALETTE = {
  dark: {
    pageBg: '#1e1e1e',
    surfaceBg: '#252526',
    surfaceToolbarBg: '#2d2d30',
    border: '#3e3e42',
    text: '#e8e8e8',
    textMuted: '#9b9b9b',
    accent: '#ff5252',
    accentFg: '#ffffff',
    accentHover: '#e23e3e',
    success: '#4ade80',
    danger: '#f87171',
    warning: '#fbbf24',
  },
  light: {
    pageBg: '#fafafa',
    surfaceBg: '#ffffff',
    surfaceToolbarBg: '#f3f3f3',
    border: '#d8d8d8',
    text: '#1f1f1f',
    textMuted: '#686868',
    accent: '#c00000',
    accentFg: '#ffffff',
    accentHover: '#a30000',
    success: '#15803d',
    danger: '#b91c1c',
    warning: '#d97706',
  },
} as const;

const SANS_STACK = "'Open Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif";

export function createSemantifyrTheme(mode: ResolvedColorMode): Theme {
  const c = PALETTE[mode];
  return createTheme({
    palette: {
      mode,
      primary: { main: c.accent, dark: c.accentHover, contrastText: c.accentFg },
      background: { default: c.pageBg, paper: c.surfaceBg },
      text: { primary: c.text, secondary: c.textMuted },
      divider: c.border,
      success: { main: c.success },
      warning: { main: c.warning },
      error: { main: c.danger },
    },
    typography: {
      fontFamily: SANS_STACK,
      button: { textTransform: 'none', fontWeight: 600 },
    },
    components: {
      // color: 'inherit' so a bare <Button> is neutral; primary actions opt in via color="primary".
      MuiButton: {
        defaultProps: { disableElevation: true, color: 'inherit' },
      },
      // Default color="primary" would paint the bar accent-red.
      MuiAppBar: {
        defaultProps: { elevation: 0, color: 'inherit' },
        styleOverrides: {
          root: {
            backgroundColor: c.surfaceToolbarBg,
            color: c.text,
            borderBottom: `1px solid ${c.border}`,
          },
        },
      },
      // Kill MUI's dark-mode elevation-overlay tint; bg comes from palette.background.paper.
      MuiPaper: {
        styleOverrides: { root: { backgroundImage: 'none' } },
      },
      MuiPopover: {
        styleOverrides: { paper: { border: `1px solid ${c.border}` } },
      },
      MuiTab: {
        styleOverrides: { root: { textTransform: 'none' } },
      },
      // Hint-only tooltips; don't stay open when the cursor crosses them.
      MuiTooltip: {
        defaultProps: { disableInteractive: true, enterDelay: 400 },
        styleOverrides: {
          tooltip: {
            backgroundColor: c.surfaceToolbarBg,
            color: c.text,
            border: `1px solid ${c.border}`,
            fontSize: FONT_SIZE.xs,
          },
          arrow: { color: c.surfaceToolbarBg },
        },
      },
    },
  });
}
