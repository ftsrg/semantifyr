/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import { ThemeProvider } from '@mui/material/styles';
import EditorPage from './components/EditorPage';
import AdminPage from './components/AdminPage';
import ReloadPrompt from './components/pwa/ReloadPrompt';
import { useColorMode } from './lib/hooks/useColorMode';
import { resolveBackendUrl } from './lib/util/backendUrl';
import { createSemantifyrTheme } from './lib/util/theme';

function isAdminRoute(): boolean {
  return typeof window !== 'undefined' && window.location.pathname.startsWith('/admin');
}

function isEmbedded(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  return new URLSearchParams(window.location.search).get('embed') === '1';
}

export default function App(): React.JSX.Element {
  const { preference, colorMode, cycle } = useColorMode();
  const backendUrl = React.useMemo(() => resolveBackendUrl(), []);
  const theme = React.useMemo(() => createSemantifyrTheme(colorMode), [colorMode]);

  const surface = isAdminRoute() ? (
    <AdminPage
      colorMode={colorMode}
      colorModePreference={preference}
      onToggleColorMode={cycle}
    />
  ) : (
    <EditorPage
      colorMode={colorMode}
      colorModePreference={preference}
      onToggleColorMode={cycle}
      backendUrl={backendUrl}
      embedded={isEmbedded()}
    />
  );

  return (
    <ThemeProvider theme={theme}>
      {surface}
      <ReloadPrompt />
    </ThemeProvider>
  );
}
