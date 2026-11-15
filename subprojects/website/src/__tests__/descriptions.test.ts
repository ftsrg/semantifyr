/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { filesUnder, readText } from './files';

const DOCS = fileURLToPath(new URL('../docs/', import.meta.url));

// the build would otherwise describe a page by the excerpt of its first paragraph
describe('every page of the docs', () => {
  const pages = filesUnder(DOCS).filter((file) => {
    return /\.mdx?$/.test(file);
  });

  it.each(pages)('%s has a description of its own', (page) => {
    const frontMatter = /^---\n([\s\S]*?)\n---/.exec(readText(`${DOCS}${page}`))?.[1] ?? '';
    expect(frontMatter).toMatch(/^description: \S.+$/m);
  });
});
