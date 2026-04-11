/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

const root = document.getElementById('root');
if (!root) {
  throw new Error('semantifyr-live: missing #root element');
}

ReactDOM.createRoot(root).render(
  <App />,
);
