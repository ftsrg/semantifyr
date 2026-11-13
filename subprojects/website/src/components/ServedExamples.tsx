/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useLocation } from '@docusaurus/router';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { liveExampleUrl, normalizeBaseUrl } from '@semantifyr/editor-common';
import { servedExamples, type ExampleLanguage, type ServedExample } from '@semantifyr/editor-common/examples';
import { useLiveBackendUrl } from '@site/src/lib/live';

function useExampleAddress(example: ServedExample): string {
  const liveBackendUrl = useLiveBackendUrl();
  return liveExampleUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, example.language, example.id);
}

function exampleOf(language: ExampleLanguage, id: string): ServedExample {
  const example = servedExamples.find((candidate) => {
    return candidate.language === language && candidate.id === id;
  });
  if (example === undefined) {
    throw new Error(`The registry serves no /${language}/${id}`);
  }
  return example;
}

// where an example comes from and under which terms, from the registry
export function ExampleRights({ example }: { example: ServedExample }): React.JSX.Element {
  return <>{`Origin: ${example.origin}. Rights: ${example.holder}, ${example.licence}.`}</>;
}

// an example by its address in live, under its label
export function ExampleLink({ language, id }: { language: ExampleLanguage; id: string }): React.JSX.Element {
  const example = exampleOf(language, id);
  return <a href={useExampleAddress(example)}>{example.label}</a>;
}

function ServedExampleItem({ example }: { example: ServedExample }): React.JSX.Element {
  return (
    <li>
      <a href={useExampleAddress(example)}>{example.label}</a>
      {`: ${example.reason} `}
      <ExampleRights example={example} />
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
