/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createTheme, type Theme } from '@mui/material/styles';
import type { ResolvedColorMode } from './colorMode';

/**
 * SVG-icon sizes (`<Icon sx={{ fontSize: ICON_SIZE.md }} />`). One named step instead of the
 * scatter of magic numbers - `sm` for inline / dense icons, `md` for panel + list controls,
 * `lg` for toolbar / header actions, `xl` for prominent indicators, `xxl` for the empty-state
 * illustration.
 */
export const ICON_SIZE = {
  sm: 16,
  md: 18,
  lg: 20,
  xl: 24,
  xxl: 40,
} as const;

/**
 * Text sizes (`sx={{ fontSize: FONT_SIZE.sm }}`). `xs` = tiny labels / chips / metadata, `sm`
 * = secondary text / list secondaries / status bar, `md` = body text / list primaries, `lg` =
 * sub-headings, `xl` = headings. Anything outside this set should earn its place.
 */
export const FONT_SIZE = {
  xs: '0.72rem',
  sm: '0.78rem',
  md: '0.85rem',
  lg: '0.95rem',
  xl: '1rem',
} as const;

/**
 * Colour values for the MUI theme, hand-mirrored from the `:root[data-theme=...]` blocks in
 * `src/styles.css`. The CSS custom properties stay the canonical token set for everything
 * styled directly (and for non-MUI bits); MUI's palette needs real hex because it does colour
 * maths (lighten/darken/alpha) on it, so it cannot read `var()`. Keep the two in sync.
 */
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

/**
 * MUI theme derived from the app's design tokens. Wrapping the app in a `<ThemeProvider>` with
 * this gives every MUI component a theme-appropriate default in both light and dark, so a new
 * component is correct without per-instance `sx` colour overrides, and the `color="primary"`
 * surfaces (ripples, focus rings, switches, selected-tab text, etc.) follow the Semantifyr
 * accent rather than MUI's stock blue. It does not replace the explicit `colorMode` plumbing
 * to Monaco and the logo - those still need the resolved mode directly.
 */
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
      MuiButton: {
        // `color: 'inherit'` so a bare `<Button>` is neutral (text inherits from its
        // surface) instead of the accent red - primary-action buttons opt in explicitly
        // with `color="primary"` / `variant="contained" color="primary"`.
        defaultProps: { disableElevation: true, color: 'inherit' },
      },
      MuiAppBar: {
        // The default `color="primary"` paints the bar with the accent; `inherit` + the
        // explicit surface colour below is what every AppBar in the app actually wants.
        defaultProps: { elevation: 0, color: 'inherit' },
        styleOverrides: {
          root: {
            backgroundColor: c.surfaceToolbarBg,
            color: c.text,
            borderBottom: `1px solid ${c.border}`,
          },
        },
      },
      MuiPaper: {
        // Kill the elevation-overlay tint MUI layers onto Paper in dark mode; the surface
        // colour comes from `palette.background.paper`. Menus, popovers, the admin Paper, etc.
        styleOverrides: { root: { backgroundImage: 'none' } },
      },
      MuiPopover: {
        // Menus inherit from Popover; a hairline border separates the floating surface from
        // the page (the elevation shadow alone is too subtle in dark mode).
        styleOverrides: { paper: { border: `1px solid ${c.border}` } },
      },
      MuiTab: {
        styleOverrides: { root: { textTransform: 'none' } },
      },
      MuiTooltip: {
        // `disableInteractive`: ours are pure hint labels (no links / selectable content), so
        // they should vanish the moment the cursor leaves the trigger - the default
        // "interactive" behaviour keeps the popup open whenever the cursor's path crosses it,
        // which reads as a stuck tooltip. `enterDelay` keeps a quick sweep across the toolbar
        // from flashing a row of them.
        defaultProps: { disableInteractive: true, enterDelay: 400 },
        // MUI's stock tooltip is a dark grey in both modes, which is low-contrast on the dark
        // surface. Use the toolbar surface + a border so it reads in either theme.
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
