/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useLocation } from '@docusaurus/router';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { liveExampleUrl, normalizeBaseUrl } from '@semantifyr/editor-common';
import { servedExamples, type ServedExample } from '@semantifyr/editor-common/examples';
import { useLiveBackendUrl } from '@site/src/lib/live';

function useExampleAddress(example: ServedExample): string {
  const liveBackendUrl = useLiveBackendUrl();
  return liveExampleUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, example.language, example.id);
}

function ServedExampleItem({ example }: { example: ServedExample }): React.JSX.Element {
  return (
    <li>
      <a href={useExampleAddress(example)}>{example.label}</a>
      {`: ${example.reason}`}
    </li>
  );
}

// the examples the registry says this page explains, each opening in live
export default function ServedExamples(): React.JSX.Element {
  const { siteConfig } = useDocusaurusContext();
  const { pathname } = useLocation();
  const page = pathname.slice(siteConfig.baseUrl.length).replace(/\/$/, '');
  const examples = servedExamples.filter((example) => {
    return example.page === page;
  });
  if (examples.length === 0) {
    throw new Error(`The registry names no example explained on ${page}`);
  }
  return (
    <ul>
      {examples.map((example) => {
        return <ServedExampleItem key={`${example.language}/${example.id}`} example={example} />;
      })}
    </ul>
  );
}
