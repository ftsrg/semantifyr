/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const REPOSITORY = fileURLToPath(new URL('../../../../', import.meta.url));
const SITE = fileURLToPath(new URL('../../', import.meta.url));

// the files outside the site that link into it, which the website's check takes as inputs
const LINKING = [
  'README.md',
  'CITATION.cff',
  'subprojects/semantifyr-vscode/README.md',
  'subprojects/semantifyr-vscode/package.json',
  'subprojects/semantifyr-live/frontend/src/texts.ts',
  'subprojects/cli-common/src/main/kotlin/hu/bme/mit/semantifyr/cli/common/RootCommand.kt',
];

const ADDRESS = /(?:https:\/\/semantifyr\.org|\$\{WEBSITE_URL\})(\/[^\s"'`)\]>#]*)?/g;

// the docs are routed at the site's root and the pages beside them
function pageExists(address: string): boolean {
  const path = address.replace(/^\/|\/$/g, '');
  if (path === '') {
    return existsSync(`${SITE}src/pages/index.tsx`);
  }
  return [
    `src/pages/${path}.tsx`,
    `src/docs/${path}.md`,
    `src/docs/${path}.mdx`,
    `src/docs/${path}/index.md`,
    `src/docs/${path}/index.mdx`,
  ].some((file) => {
    return existsSync(`${SITE}${file}`);
  });
}

describe('the addresses of the site that the repository links to', () => {
  const links = LINKING.flatMap((file) => {
    return [...readFileSync(`${REPOSITORY}${file}`, 'utf8').matchAll(ADDRESS)].map((match) => {
      return [file, match[1] ?? '/'] as const;
    });
  });

  it('are found', () => {
    expect(links.length).toBeGreaterThan(0);
  });

  it.each(links)('%s links to %s, which opens a page', (_, address) => {
    expect(pageExists(address)).toBe(true);
  });
});
