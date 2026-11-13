/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { lazy, Suspense } from 'react';
import ReactDOM from 'react-dom/client';

import ErrorBoundary from './ErrorBoundary';
import { isAdminRoute } from './admin/route';
import { APP_ERROR } from './texts';

// each route loads its own chunk, so the admin page never loads the editor
const ShellApp = lazy(() => {
  return import('./shells/ShellApp');
});
const AdminApp = lazy(() => {
  return import('./admin/AdminApp');
});

const root = document.getElementById('root');
if (!root) {
  throw new Error('semantifyr-live: missing #root element');
}

const appErrorFallback = (
  <div style={{ fontFamily: 'sans-serif', padding: '2rem', maxWidth: '36rem', margin: '0 auto', color: '#1f1f1f' }}>
    <h1 style={{ fontSize: '1.4rem' }}>Something went wrong</h1>
    <p>{APP_ERROR}</p>
    <button
      type="button"
      onClick={() => { window.location.reload(); }}
      style={{ padding: '0.5rem 1rem', fontSize: '0.95rem', cursor: 'pointer', border: 'none', borderRadius: 4, background: '#c00000', color: '#fff' }}
    >
      Reload page
    </button>
  </div>
);

ReactDOM.createRoot(root).render(
  <ErrorBoundary label="app" fallback={appErrorFallback}>
    <Suspense fallback={null}>
      {isAdminRoute(window.location.pathname) ? <AdminApp /> : <ShellApp />}
    </Suspense>
  </ErrorBoundary>,
);
