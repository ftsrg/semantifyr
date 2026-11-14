/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import linkingFiles from '../../linking-files.json';
import { addressOf, learnParts } from '../map';

const REPOSITORY = fileURLToPath(new URL('../../../../', import.meta.url));
const SITE = fileURLToPath(new URL('../../', import.meta.url));

// both hosts the site's addresses are written with, and live's constant for the first
const ADDRESS = /(?:https:\/\/semantifyr\.org|https:\/\/ftsrg\.mit\.bme\.hu\/semantifyr|\$\{WEBSITE_URL\})(\/[^\s"'`)\]>#]*)?/g;

function filesUnder(directory: string): string[] {
  return readdirSync(`${SITE}${directory}`, { recursive: true, encoding: 'utf8' }).map((file) => {
    return `${directory}/${file}`;
  });
}

// the docs are routed at the site's root unless a page names its slug, the pages beside them, and the generated listings at their slugs
function siteAddresses(): Set<string> {
  const addresses = new Set<string>(['/']);
  for (const file of filesUnder('src/docs')) {
    const text = file.endsWith('.yml') || /\.mdx?$/.test(file) ? readFileSync(`${SITE}${file}`, 'utf8') : '';
    const slug = /^\s*slug: (.+)$/m.exec(text)?.[1];
    if (slug !== undefined) {
      addresses.add(`${slug.replace(/\/$/, '')}/`);
    }
    if (/\.mdx?$/.test(file) && slug === undefined) {
      addresses.add(`/${file.slice('src/docs/'.length).replace(/\.mdx?$/, '').replace(/(^|\/)index$/, '')}/`.replace(/\/+/g, '/'));
    }
  }
  for (const file of filesUnder('src/pages')) {
    if (file.endsWith('.tsx')) {
      addresses.add(`/${file.slice('src/pages/'.length).replace(/\.tsx$/, '').replace(/(^|\/)index$/, '')}/`.replace(/\/+/g, '/'));
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
    return [...readFileSync(`${REPOSITORY}${file}`, 'utf8').matchAll(ADDRESS)].map((match) => {
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
