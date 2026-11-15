/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Admonition from '@theme/Admonition';
import { findConstruct } from '@site/src/components/Constructs';

interface Props {
  children: React.ReactNode;
  // a planned construct of the language fails the build once the parser takes it
  table?: string;
  construct?: string;
}

// the one mark of what is planned: a page never writes a planned thing as present
export default function Planned({ children, table, construct }: Props): React.JSX.Element {
  if (table !== undefined && construct !== undefined) {
    const { outcome } = findConstruct(table, construct);
    if (outcome !== 'parse') {
      throw new Error(`constructs.tsv says the parser no longer rejects ${construct} (${outcome}); update the page that marks it Planned`);
    }
  }
  return (
    <Admonition type="info" title="Planned">
      {children}
    </Admonition>
  );
}
