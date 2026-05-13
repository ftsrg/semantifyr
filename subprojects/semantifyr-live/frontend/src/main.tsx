/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import ReactDOM from 'react-dom/client';

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
import App from './App';
import ErrorBoundary from './components/shell/ErrorBoundary';
import './styles.css';

const root = document.getElementById('root');
if (!root) {
  throw new Error('semantifyr-live: missing #root element');
}

const appErrorFallback = (
  <div style={{ fontFamily: 'sans-serif', padding: '2rem', maxWidth: '36rem', margin: '0 auto', color: '#1f1f1f' }}>
    <h1 style={{ fontSize: '1.4rem' }}>Something went wrong</h1>
    <p>The app hit an unexpected error. Reloading the page usually fixes it.</p>
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
    <App />
  </ErrorBoundary>,
);
