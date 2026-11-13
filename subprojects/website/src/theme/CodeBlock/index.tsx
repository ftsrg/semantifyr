/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { Suspense, lazy, useContext } from 'react';
import BrowserOnly from '@docusaurus/BrowserOnly';
import CodeBlock from '@theme-original/CodeBlock';
import type CodeBlockType from '@theme/CodeBlock';
import type { WrapperProps } from '@docusaurus/types';
import { FenceContext, OpenLinkContext } from '@site/src/lib/fence';

const OpenInLiveLink = lazy(() => import('./OpenInLiveLink'));

type Props = WrapperProps<typeof CodeBlockType>;

// Code block languages that are also live flavor ids.
const LIVE_FLAVORS = new Set(['oxsts', 'gamma']);

// Opt in per code block through the metastring: ```oxsts open
const OPEN_KEYWORD = /(^|\s)open(\s|$)/;

function extractLanguage(props: Props): string | null {
  const fromLanguageProp = (props as { language?: string }).language;
  if (fromLanguageProp && LIVE_FLAVORS.has(fromLanguageProp)) {
    return fromLanguageProp;
  }
  const className = (props as { className?: string }).className;
  const languageId = className ? /language-(\w+)/.exec(className)?.[1] : undefined;
  return languageId && LIVE_FLAVORS.has(languageId) ? languageId : null;
}

function extractText(node: React.ReactNode): string {
  if (node == null || typeof node === 'boolean') {
    return '';
  }
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map(extractText).join('');
  }
  if (React.isValidElement(node)) {
    const element = node as React.ReactElement<{ children?: React.ReactNode }>;
    return extractText(element.props.children);
  }
  return '';
}

export default function CodeBlockWrapper(props: Props): React.JSX.Element {
  const language = extractLanguage(props);
  const metastring = (props as { metastring?: string }).metastring ?? '';
  const open = OPEN_KEYWORD.test(metastring);
  const fence = useContext(FenceContext);
  const code = language !== null && open ? fence?.model ?? extractText((props as { children?: React.ReactNode }).children) : '';

  const link = code && language
    ? (
      <BrowserOnly>
        {() => (
          <Suspense fallback={null}>
            <OpenInLiveLink language={language} code={code} address={fence?.address} />
          </Suspense>
        )}
      </BrowserOnly>
    )
    : null;

  return (
    <OpenLinkContext.Provider value={link}>
      <CodeBlock {...props} />
    </OpenLinkContext.Provider>
  );
}
