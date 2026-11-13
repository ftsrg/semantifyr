/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import CodeBlock from '@theme/CodeBlock';
import { EmbeddedExample } from '@site/src/components/LiveEmbed/EmbeddedExample';
import { FenceContext } from '@site/src/lib/fence';

interface Props {
  model: string;
  shown: string;
  lines?: string;
  embed?: 'run' | 'edit';
  open?: boolean;
}

// an oxsts fence as remarkFences leaves it, when what it shows is not its whole model or it is embedded or opened
export default function Fence({ model, shown, lines, embed, open = false }: Props): React.JSX.Element {
  if (embed !== undefined) {
    const [first, last] = (lines ?? '').split('-').map(Number);
    return (
      <EmbeddedExample
        model={model}
        shown={shown}
        lines={first === undefined || last === undefined ? undefined : { first, last }}
        language="oxsts"
        view={embed === 'run' ? 'embed' : 'edit-only'}
        open={open}
      />
    );
  }
  return (
    <FenceContext.Provider value={{ model, control: null }}>
      <CodeBlock language="oxsts" metastring={open ? 'open' : ''}>
        {shown}
      </CodeBlock>
    </FenceContext.Provider>
  );
}
