/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const DOCS = fileURLToPath(new URL('../docs/', import.meta.url));

// the build would otherwise describe a page by the excerpt of its first paragraph
describe('every page of the docs', () => {
  const pages = readdirSync(DOCS, { recursive: true, encoding: 'utf8' }).filter((file) => {
    return /\.mdx?$/.test(file);
  });

  it.each(pages)('%s has a description of its own', (page) => {
    const frontMatter = /^---\n([\s\S]*?)\n---/.exec(readFileSync(`${DOCS}${page}`, 'utf8'))?.[1] ?? '';
    expect(frontMatter).toMatch(/^description: \S.+$/m);
  });
});
