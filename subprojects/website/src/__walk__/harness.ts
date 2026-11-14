/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { mkdirSync, readFileSync } from 'node:fs';
import { extname, join, resolve } from 'node:path';
import type { Browser, BrowserContext, Page } from 'playwright';
import { expect, inject, test as base } from 'vitest';
import { MockBackend } from '../../../semantifyr-live/frontend/src/__ui__/mock/backend';
import { launchChromium } from './browser';
import { contentTypeOf, fileOf, LIVE } from './staticServer';

export const SITE_URL = inject('siteUrl');
const LIVE_URL = inject('liveUrl');

const EVIDENCE = resolve(import.meta.dirname, '../../build/evidence/walk');

export interface OpenOptions {
  width?: number;
  height?: number;
}

export interface Walk {
  backend: MockBackend;
  // the first page fixes the context's viewport
  open: (address: string, options?: OpenOptions) => Promise<Page>;
}

const DESKTOP = { width: 1440, height: 900 };

// the routes stand before the first page loads, so no request of a page reaches another host than the site's server; a route sees HTTP requests alone, and the mock answers live's sockets
async function routeHosts(context: BrowserContext, backend: MockBackend): Promise<void> {
  const siteOrigin = new URL(SITE_URL).origin;
  await context.route((url) => {
    return url.origin !== siteOrigin;
  }, async (route) => {
    await route.abort('blockedbyclient');
  });
  await context.route(`${LIVE_URL}/**`, async (route) => {
    const path = new URL(route.request().url()).pathname;
    const file = fileOf(LIVE, path) ?? (extname(path) === '' ? join(LIVE, 'index.html') : null);
    if (file === null) {
      await route.fulfill({ status: 404 });
      return;
    }
    await route.fulfill({ contentType: contentTypeOf(file), body: readFileSync(file) });
  });
  await backend.install(context, LIVE_URL);
}

function folderName(name: string): string {
  return name.replace(/[^a-zA-Z0-9-]+/g, '-').replace(/^-|-$/g, '').slice(0, 120);
}

export const test = base.extend<{ walk: Walk; browser: Browser }>({
  browser: [
    // vitest reads the fixtures a fixture needs from the pattern of its first parameter, and this one needs none
    // eslint-disable-next-line no-empty-pattern
    async ({}, provide) => {
      const browser = await launchChromium();
      await provide(browser);
      await browser.close();
    },
    { scope: 'worker' },
  ],
  walk: async ({ browser, task }, provide) => {
    const backend = new MockBackend();
    const pageErrors: string[] = [];
    let context: BrowserContext | null = null;
    const contextFor = async (options: OpenOptions): Promise<BrowserContext> => {
      if (context) {
        return context;
      }
      context = await browser.newContext({
        viewport: { width: options.width ?? DESKTOP.width, height: options.height ?? DESKTOP.height },
        serviceWorkers: 'block',
      });
      await context.tracing.start({ screenshots: true, snapshots: true });
      await routeHosts(context, backend);
      return context;
    };
    await provide({
      backend,
      open: async (address, options = {}) => {
        const page = await (await contextFor(options)).newPage();
        page.on('pageerror', (error) => {
          pageErrors.push(`${page.url()}: ${error.stack ?? error.message}`);
        });
        await page.goto(new URL(address.replace(/^\//, ''), SITE_URL).toString());
        return page;
      },
    });
    const problems = [
      ...backend.problems.map((problem) => {
        return `mock backend: ${problem}`;
      }),
      ...pageErrors.map((error) => {
        return `uncaught in the page: ${error}`;
      }),
    ];
    const opened = context as BrowserContext | null;
    if (opened) {
      if (task.result?.state === 'fail' || problems.length > 0) {
        const folder = join(EVIDENCE, folderName(task.name));
        mkdirSync(folder, { recursive: true });
        let index = 0;
        for (const page of opened.pages()) {
          await page.screenshot({ path: join(folder, `page-${index++}.png`) }).catch(() => {});
        }
        await opened.tracing.stop({ path: join(folder, 'trace.zip') });
      } else {
        await opened.tracing.stop();
      }
      await opened.close();
    }
    expect(problems, 'the mock backend answered everything and nothing was uncaught in the page').toEqual([]);
  },
});
