/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect } from 'vitest';
import { learnParts } from '../map';
import { test } from './harness';

// Goal: the site's own interactive parts work in the built site: an embedded example opens live's editor in place and closes back to its control, the header's Learn menu opens with Learn's parts, and the search opens a page it finds

// a lesson, which embeds its stages
const LESSON = '/learn/language/lessons/modeling-and-verification/';

function normalized(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}

describe('the built site', () => {
  test('opens an embedded example in live\'s editor in place and closes it back to its control', async ({ ui }) => {
    const page = await ui.open(LESSON);
    const example = page.locator('article [aria-busy]').first();
    const control = example.getByRole('button', { name: 'Open', exact: true });
    const code = example.locator('pre');
    const firstLine = normalized((await code.innerText()).split('\n')[0] ?? '');
    expect(firstLine, 'the example shows a model').not.toBe('');
    await control.click();

    const frame = example.getByTitle('Semantifyr Live');
    await expect.poll(async () => {
      return normalized(await frame.contentFrame().locator('.monaco-editor .view-lines').first().innerText().catch(() => {
        return '';
      }));
    }, { timeout: 120_000 }).toContain(firstLine);
    await expect.poll(async () => {
      return code.isHidden();
    }, { message: 'the editor has taken the code block\'s place' }).toBe(true);
    expect(await frame.isVisible()).toBe(true);
    await example.getByRole('button', { name: 'Close the editor' }).click();

    await expect.poll(async () => {
      return frame.count();
    }).toBe(0);
    expect(await code.isVisible()).toBe(true);
    expect(await control.evaluate((element) => {
      return element === document.activeElement;
    }), 'the focus is back on the example\'s control').toBe(true);
  });

  test('opens the header\'s Learn menu with Learn\'s parts', async ({ ui }) => {
    const page = await ui.open('/');
    const toggle = page.getByRole('button', { name: 'Parts of Learn' });
    await toggle.click();

    expect(await toggle.getAttribute('aria-expanded')).toBe('true');
    const menu = page.getByRole('navigation', { name: 'Sections' }).getByRole('list');
    expect(await menu.getByRole('link').allInnerTexts()).toEqual(learnParts.map((part) => {
      return part.label;
    }));
  });

  test('searches for a page and opens the result it found', async ({ ui }) => {
    const page = await ui.open('/');
    const search = page.getByRole('combobox', { name: 'Search', exact: true }).or(page.getByRole('textbox', { name: 'Search', exact: true }));
    const first = page.url();
    await search.click();
    // the search loads its index on its first focus and becomes a combobox once it has it
    await expect.poll(async () => {
      return search.getAttribute('role');
    }).toBe('combobox');
    await search.pressSequentially('trace');
    const result = page.getByRole('option').first();
    await expect.poll(async () => {
      return result.count();
    }).toBe(1);
    const resultText = await result.innerText();
    await result.click();

    await expect.poll(() => {
      return page.url();
    }).not.toBe(first);
    const title = await page.getByRole('heading', { level: 1 }).innerText();
    expect(resultText, 'the opened page is the one the result names').toContain(title);
  });
});
