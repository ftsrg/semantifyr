/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { BACKENDS } from '../map';

const PAGES = fileURLToPath(new URL('../docs/learn/reference/backends/', import.meta.url));

describe('the backends the architecture names', () => {
  it('are the backends the reference has a page for, at their addresses', () => {
    const pages = readdirSync(PAGES).filter((file) => {
      return file.endsWith('.mdx');
    }).map((file) => {
      const title = /^title: (.+)$/m.exec(readFileSync(`${PAGES}${file}`, 'utf8'))?.[1];
      return { name: title, page: `/learn/reference/backends/${file.replace(/\.mdx$/, '')}/` };
    });
    const byName = (left: { name: string | undefined }, right: { name: string | undefined }): number => {
      return (left.name ?? '').localeCompare(right.name ?? '');
    };
    expect([...BACKENDS].sort(byName)).toEqual(pages.sort(byName));
  });
});
