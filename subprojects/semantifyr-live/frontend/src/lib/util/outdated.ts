/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export async function isPageOutdated(): Promise<boolean> {
  const entry = document.querySelector('script[type="module"][src]')?.getAttribute('src');
  if (!entry) {
    return false;
  }
  const response = await fetch('/', { cache: 'no-store' });
  return response.ok && !(await response.text()).includes(`src="${entry}"`);
}
