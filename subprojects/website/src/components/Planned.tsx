/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Admonition from '@theme/Admonition';

interface Props {
  children: React.ReactNode;
}

// the one mark of what is planned: a page never writes a planned thing as present
export default function Planned({ children }: Props): React.JSX.Element {
  return (
    <Admonition type="info" title="Planned">
      {children}
    </Admonition>
  );
}
