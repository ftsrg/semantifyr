/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import linkingFiles from '../../linking-files.json';
import { addressOf, learnParts } from '../map';
import { filesUnder, readText } from './files';

const REPOSITORY = fileURLToPath(new URL('../../../../', import.meta.url));
const SITE = fileURLToPath(new URL('../../', import.meta.url));

// the site's address, and live's constant for it
const ADDRESS = /(?:https:\/\/semantifyr\.org|\$\{WEBSITE_URL\})(\/[^\s"'`)\]>#]*)?/g;

// the docs are routed at the site's root unless a page names its slug, the pages beside them, and the generated listings at their slugs
function siteAddresses(): Set<string> {
  const addresses = new Set<string>(['/']);
  for (const file of filesUnder(`${SITE}src/docs`)) {
    const text = file.endsWith('.yml') || /\.mdx?$/.test(file) ? readText(`${SITE}src/docs/${file}`) : '';
    const slug = /^\s*slug: (.+)$/m.exec(text)?.[1];
    if (slug !== undefined) {
      addresses.add(`${slug.replace(/\/$/, '')}/`);
    }
    if (/\.mdx?$/.test(file) && slug === undefined) {
      addresses.add(`/${file.replace(/\.mdx?$/, '').replace(/(^|\/)index$/, '')}/`.replace(/\/+/g, '/'));
    }
  }
  for (const file of filesUnder(`${SITE}src/pages`)) {
    if (file.endsWith('.tsx')) {
      addresses.add(`/${file.replace(/\.tsx$/, '').replace(/(^|\/)index$/, '')}/`.replace(/\/+/g, '/'));
    }
  }
  for (const part of learnParts) {
    addresses.add(addressOf(part));
  }
  return addresses;
}

describe('the addresses of the site that the repository links to', () => {
  const addresses = siteAddresses();
  const links = linkingFiles.flatMap((file) => {
    return [...readText(`${REPOSITORY}${file}`).matchAll(ADDRESS)].map((match) => {
      return [file, `${(match[1] ?? '/').replace(/\/$/, '')}/`] as const;
    });
  });

  it('are found', () => {
    expect(links.length).toBeGreaterThan(0);
  });

  it.each(links)('%s links to %s, which opens a page', (_, address) => {
    expect(addresses.has(address)).toBe(true);
  });
});
