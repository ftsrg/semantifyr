/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import '@fontsource/open-sans/latin-400.css';
import '@fontsource/open-sans/latin-ext-400.css';
import '@fontsource/open-sans/latin-600.css';
import '@fontsource/open-sans/latin-ext-600.css';
import '@fontsource/open-sans/latin-700.css';
import '@fontsource/open-sans/latin-ext-700.css';
import '@fontsource/jetbrains-mono/latin-400.css';
import '@fontsource/jetbrains-mono/latin-ext-400.css';
import '@fontsource/jetbrains-mono/latin-700.css';
import '@fontsource/jetbrains-mono/latin-ext-700.css';
import React, { Suspense, lazy, useState } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import { parseLiveEditorUrl } from '@semantifyr/editor-common';
import AdminPage from './components/AdminPage';
import EditorLoading from './components/editor/EditorLoading';
import ReloadPrompt from './components/pwa/ReloadPrompt';
import { useColorMode } from './lib/hooks/useColorMode';
import { createSettingsStore, createSystemColorModeStore } from './lib/stores/settings';
import { createSemantifyrTheme } from './lib/util/theme';
import './styles.css';

// The editor and its language machinery load as one chunk, after the first paint
const EditorRoot = lazy(() => {
  return import('./components/EditorRoot')
});

function isAdminRoute(): boolean {
  return typeof window !== 'undefined' && window.location.pathname.startsWith('/admin');
}

function isEmbedded(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  return parseLiveEditorUrl(window.location.search).embed;
}

export default function App(): React.JSX.Element {
  const [settings] = useState(createSettingsStore);
  const [systemColorMode] = useState(createSystemColorModeStore);
  const { preference, colorMode, cycle } = useColorMode(settings, systemColorMode);
  const theme = React.useMemo(() => createSemantifyrTheme(colorMode), [colorMode]);

  const surface = isAdminRoute() ? (
    <AdminPage
      colorMode={colorMode}
      colorModePreference={preference}
      onToggleColorMode={cycle}
    />
  ) : (
    <Suspense fallback={<EditorLoading message="Loading editor..." />}>
      <EditorRoot
        settings={settings}
        systemColorMode={systemColorMode}
        colorMode={colorMode}
        colorModePreference={preference}
        onToggleColorMode={cycle}
        backendUrl={window.location.origin}
        embedded={isEmbedded()}
      />
    </Suspense>
  );

  return (
    <ThemeProvider theme={theme}>
      {surface}
      <ReloadPrompt />
    </ThemeProvider>
  );
}
