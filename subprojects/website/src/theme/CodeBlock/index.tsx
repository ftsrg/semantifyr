/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { Suspense, lazy, useContext } from 'react';
import BrowserOnly from '@docusaurus/BrowserOnly';
import CodeBlock from '@theme-original/CodeBlock';
import type { Props } from '@theme/CodeBlock';
import { FenceContext, OpenLinkContext } from '@site/src/lib/fence';

const OpenInLiveLink = lazy(() => {
  return import('./OpenInLiveLink');
});

const LIVE_FLAVORS = new Set([
  'oxsts',
  'gamma',
]);

export default function CodeBlockWrapper(props: Props): React.JSX.Element {
  const fence = useContext(FenceContext);
  const { language, metastring } = props;
  const link = fence !== null && language !== undefined && LIVE_FLAVORS.has(language) && metastring === 'open'
    ? (
      <BrowserOnly>
        {() => {
          return (
            <Suspense fallback={null}>
              <OpenInLiveLink language={language} code={fence.model} address={fence.address} />
            </Suspense>
          );
        }}
      </BrowserOnly>
    )
    : null;

  return (
    <OpenLinkContext.Provider value={link}>
      <CodeBlock {...props} />
    </OpenLinkContext.Provider>
  );
}
