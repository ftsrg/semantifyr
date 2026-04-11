/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Auto-unmount React trees between tests so each test sees a clean DOM. Without this,
// the testing-library hooks pile up and queries get noisy.
afterEach(() => {
  cleanup();
});
