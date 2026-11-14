/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { chromium, type Browser } from 'playwright';

const NAMING = 'Name a Chromium with SCREEN_TEST_CHROMIUM=<path to its executable>, or install Playwright\'s with npx playwright install chromium.';

const localChromium = process.env.SCREEN_TEST_CHROMIUM ?? '';

export function launchChromium(): Promise<Browser> {
  return chromium.launch(localChromium === '' ? {} : { executablePath: localChromium });
}

// Playwright's Chromium is installed by the walk's run rather than by a task of its own, so that a walk served from the cache fetches nothing
export function setup(): void {
  if (localChromium !== '') {
    if (!existsSync(localChromium)) {
      throw new Error(`The keyboard walk needs Chromium, and SCREEN_TEST_CHROMIUM names ${localChromium}, which does not exist. ${NAMING}`);
    }
    return;
  }
  if (existsSync(chromium.executablePath())) {
    return;
  }
  const playwrightCommandLine = join(dirname(createRequire(import.meta.url).resolve('playwright/package.json')), 'cli.js');
  // a Linux runner of CI lacks the libraries the browser needs, which Playwright installs there
  const dependencies = process.env.CI !== undefined && process.platform === 'linux' ? ['--with-deps'] : [];
  try {
    execFileSync(process.execPath, [playwrightCommandLine, 'install', ...dependencies, 'chromium'], { stdio: 'inherit' });
  } catch (error) {
    throw new Error(`The keyboard walk needs Chromium, and Playwright's could not be installed: ${error instanceof Error ? error.message : String(error)}. ${NAMING}`);
  }
  if (!existsSync(chromium.executablePath())) {
    throw new Error(`The keyboard walk needs Chromium, and Playwright's is not at ${chromium.executablePath()} after its install. ${NAMING}`);
  }
}
