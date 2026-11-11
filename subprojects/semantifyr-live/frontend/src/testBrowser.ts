/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { chromium, firefox, webkit, type Browser, type LaunchOptions, type Page } from 'playwright'

const ENGINES = {
  chromium,
  firefox,
  webkit,
}

export type TestBrowser = keyof typeof ENGINES

function testBrowserOf(value: string | undefined): TestBrowser {
  const name = value === undefined || value === '' ? 'chromium' : value
  if (!Object.hasOwn(ENGINES, name)) {
    throw new Error(`TEST_BROWSER is ${name}, expected one of ${Object.keys(ENGINES).join(', ')}`)
  }
  return name as TestBrowser
}

export const TEST_BROWSER = testBrowserOf(process.env.TEST_BROWSER)

// a local Chromium instead of Playwright's download, as on a developer machine
const chromiumPath = process.env.SCREEN_TEST_CHROMIUM

export const LAUNCH_OPTIONS: LaunchOptions = TEST_BROWSER === 'chromium' && chromiumPath ? { executablePath: chromiumPath } : {}

export function launchTestBrowser(): Promise<Browser> {
  return ENGINES[TEST_BROWSER].launch(LAUNCH_OPTIONS)
}

export interface EditorKeys {
  command: 'Control' | 'Meta'
  endOfDocument: string
}

// the editor binds the keys of the platform the user agent names, and Playwright's WebKit names a Mac on every platform
export async function editorKeysOf(page: Page): Promise<EditorKeys> {
  const mac = await page.evaluate(() => {
    return navigator.userAgent.includes('Macintosh')
  })
  return mac ? { command: 'Meta', endOfDocument: 'Meta+ArrowDown' } : { command: 'Control', endOfDocument: 'Control+End' }
}
