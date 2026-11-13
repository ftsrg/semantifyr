/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { EXAMPLE_LANGUAGES, servedExamples, servedLanguages } from '@semantifyr/editor-common/examples';
import { describe, expect, it } from 'vitest';

const DOCS = fileURLToPath(new URL('../docs/', import.meta.url));

// the docs are routed at the site's root, so a page's path names its file
function pageExists(page: string): boolean {
  return [
    `${page}.md`,
    `${page}.mdx`,
    `${page}/index.md`,
    `${page}/index.mdx`,
  ].some((file) => {
    return existsSync(`${DOCS}${file}`);
  });
}

describe('the pages live links to', () => {
  it.each(servedExamples.map((example) => {
    return [`/${example.language}/${example.id}`, example.page] as const;
  }))('the page explaining %s, %s, exists', (_, page) => {
    expect(pageExists(page)).toBe(true);
  });

  it.each(EXAMPLE_LANGUAGES.map((language) => {
    return [language, servedLanguages[language].page] as const;
  }))('the page on %s, %s, exists', (_, page) => {
    expect(pageExists(page)).toBe(true);
  });
});
