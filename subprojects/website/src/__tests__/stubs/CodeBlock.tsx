/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useContext, type ReactNode } from 'react';
import { FenceContext } from '../../lib/fence';

export default function CodeBlock({ children }: { children: ReactNode }): React.JSX.Element {
  const fence = useContext(FenceContext);
  return (
    <div>
      <pre>{children}</pre>
      {fence?.control}
    </div>
  );
}
