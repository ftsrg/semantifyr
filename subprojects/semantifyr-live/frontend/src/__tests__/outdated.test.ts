/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { isPageOutdated } from '../lib/util/outdated';

function servePage(html: string, status = 200): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(() => Promise.resolve(new Response(html, { status })));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function runEntry(src: string): void {
  const script = document.createElement('script');
  script.type = 'module';
  script.setAttribute('src', src);
  document.head.appendChild(script);
}

describe('isPageOutdated', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.head.innerHTML = '';
  });

  it('is current while the server serves the entry this page runs', async () => {
    runEntry('/assets/index-aaaa.js');
    const fetchMock = servePage('<script type="module" crossorigin src="/assets/index-aaaa.js"></script>');

    expect(await isPageOutdated()).toBe(false);
    expect(fetchMock).toHaveBeenCalledWith('/', { cache: 'no-store' });
  });

  it('is outdated once the server serves another entry', async () => {
    runEntry('/assets/index-aaaa.js');
    servePage('<script type="module" crossorigin src="/assets/index-bbbb.js"></script>');

    expect(await isPageOutdated()).toBe(true);
  });

  it('is current when the server does not answer with a page', async () => {
    runEntry('/assets/index-aaaa.js');
    servePage('', 502);

    expect(await isPageOutdated()).toBe(false);
  });

  it('does not ask the server without a module entry', async () => {
    const fetchMock = servePage('');

    expect(await isPageOutdated()).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
