/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ReactNode } from 'react';

export default function BrowserOnly({ children }: { children: () => ReactNode }): ReactNode {
  return children();
}
