/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import EditorPage from './components/EditorPage';
import AdminPage from './components/AdminPage';
import { useColorMode } from './lib/theme';
import { resolveBackendUrl } from './lib/config';

function isAdminRoute(): boolean {
  return typeof window !== 'undefined' && window.location.pathname.startsWith('/admin');
}

export default function App(): React.JSX.Element {
  const { preference, colorMode, cycle } = useColorMode();
  const backendUrl = React.useMemo(() => resolveBackendUrl(), []);

  if (isAdminRoute()) {
    return <AdminPage />;
  }

  return (
    <EditorPage
      colorMode={colorMode}
      colorModePreference={preference}
      onToggleColorMode={cycle}
      backendUrl={backendUrl}
    />
  );
}
