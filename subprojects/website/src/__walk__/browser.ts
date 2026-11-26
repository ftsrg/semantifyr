/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { chromium, firefox, webkit, type Browser, type BrowserType } from 'playwright';

const BROWSERS: Readonly<Record<string, BrowserType>> = {
  chromium,
  firefox,
  webkit,
};

function browserTypeOf(name: string): BrowserType {
  const type = BROWSERS[name];
  if (type === undefined) {
    throw new Error(`TEST_BROWSER names ${name}, which is none of ${Object.keys(BROWSERS).join(', ')}`);
  }
  return type;
}

const browserName = process.env.TEST_BROWSER ?? 'chromium';
const browserType = browserTypeOf(browserName);

const NAMING = `Install Playwright's with npx playwright install ${browserName}, or for Chromium name one with SCREEN_TEST_CHROMIUM=<path to its executable>.`;

const localChromium = browserName === 'chromium' ? process.env.SCREEN_TEST_CHROMIUM ?? '' : '';

export function launchBrowser(): Promise<Browser> {
  return browserType.launch(localChromium === '' ? {} : { executablePath: localChromium });
}

// Playwright's browser is installed by the walk's run rather than by a task of its own, so that a walk served from the cache fetches nothing
export function setup(): void {
  if (localChromium !== '') {
    if (!existsSync(localChromium)) {
      throw new Error(`The keyboard walk needs Chromium, and SCREEN_TEST_CHROMIUM names ${localChromium}, which does not exist. ${NAMING}`);
    }
    return;
  }
  if (existsSync(browserType.executablePath())) {
    return;
  }
  const playwrightCommandLine = join(dirname(createRequire(import.meta.url).resolve('playwright/package.json')), 'cli.js');
  // a Linux runner of CI lacks the libraries the browser needs, which Playwright installs there
  const dependencies = process.env.CI !== undefined && process.platform === 'linux' ? ['--with-deps'] : [];
  try {
    execFileSync(process.execPath, [playwrightCommandLine, 'install', ...dependencies, browserName], { stdio: 'inherit' });
  } catch (error) {
    throw new Error(`The keyboard walk needs ${browserName}, and Playwright's could not be installed: ${error instanceof Error ? error.message : String(error)}. ${NAMING}`);
  }
  if (!existsSync(browserType.executablePath())) {
    throw new Error(`The keyboard walk needs ${browserName}, and Playwright's is not at ${browserType.executablePath()} after its install. ${NAMING}`);
  }
}
