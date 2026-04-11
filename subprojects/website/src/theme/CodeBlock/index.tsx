/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { Suspense, lazy } from 'react';
import BrowserOnly from '@docusaurus/BrowserOnly';
import CodeBlock from '@theme-original/CodeBlock';
import type CodeBlockType from '@theme/CodeBlock';
import type { WrapperProps } from '@docusaurus/types';

// Lazy-load the lightweight addons so docs pages without an opt-in metastring pay zero
// bundle cost. Both chunks are tiny — no Monaco, no @codingame.
const VerifyButton = lazy(() => import('./VerifyButton'));
const OpenInLiveLink = lazy(() => import('./OpenInLiveLink'));

type Props = WrapperProps<typeof CodeBlockType>;

// Languages that have a flavor in the live backend's FlavorRegistry. Keep in sync with
// subprojects/semantifyr-live/backend/src/main/kotlin/.../FlavorRegistry.kt.
const SUPPORTED_FLAVORS = new Set(['oxsts', 'xsts', 'gamma']);

// Code blocks are inert by default — most snippets in the docs are excerpts that wouldn't
// compile on their own. Two opt-in addons are available, picked by the fenced code
// block's metastring:
//
//   ```oxsts verify                            ← "Verify this snippet" button
//   ```oxsts open                              ← "Open in live editor ↗" link, code inline
//   ```oxsts open=trafficlight-direct-snapshot ← link to a named example in the registry
//
// Multiple keywords can be combined: `verify open`, `verify open=...`. Anything else in
// the metastring is passed through to the default Docusaurus CodeBlock unchanged.
const VERIFY_KEYWORDS = /(^|\s)verify(\s|$)/;
const OPEN_KEYWORDS = /(^|\s)open(=([\w-]+))?(\s|$)/;

interface MetaFlags {
  verify: boolean;
  open: boolean;
  /** Optional id of a snippet registered in the live frontend's examples.ts. */
  exampleId?: string;
}

function parseMeta(metastring: string | undefined): MetaFlags {
  if (!metastring) return { verify: false, open: false };
  const verify = VERIFY_KEYWORDS.test(metastring);
  const openMatch = OPEN_KEYWORDS.exec(metastring);
  return {
    verify,
    open: !!openMatch,
    exampleId: openMatch?.[3],
  };
}

function extractLanguage(props: Props): string | null {
  // Docusaurus may pass either a className like "language-oxsts" or a `language` prop.
  const fromLanguageProp = (props as { language?: string }).language;
  if (fromLanguageProp && SUPPORTED_FLAVORS.has(fromLanguageProp)) {
    return fromLanguageProp;
  }
  const className = (props as { className?: string }).className;
  if (className) {
    const match = className.match(/language-(\w+)/);
    if (match && SUPPORTED_FLAVORS.has(match[1])) {
      return match[1];
    }
  }
  return null;
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
  const metastring = (props as { metastring?: string }).metastring;
  const meta = parseMeta(metastring);
  const wantAddon = language !== null && (meta.verify || meta.open);
  const code = wantAddon ? extractText((props as { children?: React.ReactNode }).children) : '';

  return (
    <>
      <CodeBlock {...props} />
      {wantAddon && language && (
        <BrowserOnly>
          {() => (
            <Suspense fallback={null}>
              {meta.verify && <VerifyButton language={language} code={code} />}
              {meta.open && (
                <OpenInLiveLink language={language} code={code} exampleId={meta.exampleId} />
              )}
            </Suspense>
          )}
        </BrowserOnly>
      )}
    </>
  );
}
