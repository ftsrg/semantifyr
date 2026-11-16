/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useContext, type ReactNode } from 'react';
import { FenceContext, OpenLinkContext } from '../../lib/fence';

// the theme's code block shows its text, and its buttons the fence's control and the link into live
export default function CodeBlock({ children }: { children: ReactNode }): React.JSX.Element {
  const fence = useContext(FenceContext);
  const openLink = useContext(OpenLinkContext);
  return (
    <div>
      <pre>{children}</pre>
      {fence?.control}
      {openLink}
    </div>
  );
}
