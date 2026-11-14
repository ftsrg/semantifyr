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
import { CONTENT_TYPES, fileOf, LIVE } from './staticServer';

export const SITE_URL = inject('siteUrl');

const EVIDENCE = resolve(import.meta.dirname, '../../build/evidence/walk');

export interface OpenOptions {
  width?: number;
  height?: number;
}

export interface Walk {
  backend: MockBackend;
  // opens an address of the site under its base path; the first page fixes the context's viewport
  open: (address: string, options?: OpenOptions) => Promise<Page>;
}

const DESKTOP = { width: 1440, height: 900 };

// live's production build answers at the address the site's build names for live, with the mock backend of live's interface tests behind it
async function serveLive(context: BrowserContext, backend: MockBackend, liveUrl: string): Promise<void> {
  await context.route(`${liveUrl}/**`, async (route) => {
    const path = new URL(route.request().url()).pathname;
    const file = fileOf(LIVE, path) ?? (extname(path) === '' ? join(LIVE, 'index.html') : null);
    if (file === null) {
      await route.fulfill({ status: 404 });
      return;
    }
    await route.fulfill({ contentType: CONTENT_TYPES[extname(file)] ?? 'application/octet-stream', body: readFileSync(file) });
  });
  await backend.install(context, liveUrl);
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
    const siteOrigin = new URL(SITE_URL).origin;
    let context: BrowserContext | null = null;
    let liveUrl: string | null = null;
    const contextFor = async (options: OpenOptions): Promise<BrowserContext> => {
      if (context) {
        return context;
      }
      context = await browser.newContext({
        viewport: { width: options.width ?? DESKTOP.width, height: options.height ?? DESKTOP.height },
        serviceWorkers: 'block',
      });
      await context.tracing.start({ screenshots: true, snapshots: true });
      // the walk reaches no other host than the site and live
      await context.route((url) => {
        return url.origin !== siteOrigin;
      }, async (route) => {
        await route.abort('blockedbyclient');
      });
      return context;
    };
    await provide({
      backend,
      open: async (address, options = {}) => {
        const opened = await contextFor(options);
        const page = await opened.newPage();
        page.on('pageerror', (error) => {
          pageErrors.push(`${page.url()}: ${error.stack ?? error.message}`);
        });
        await page.goto(new URL(address.replace(/^\//, ''), SITE_URL).toString());
        if (liveUrl === null) {
          const named = await page.locator('a', { hasText: 'Open Live' }).first().getAttribute('href');
          if (named === null) {
            throw new Error('The site\'s header has no link that opens live');
          }
          liveUrl = new URL(named).origin;
          await serveLive(opened, backend, liveUrl);
        }
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
