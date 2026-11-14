/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Locator, Page } from 'playwright';
import { describe, expect } from 'vitest';
import { learnParts, sections } from '../map';
import { test } from './harness';

// Goal: a keyboard alone reaches every control of the site, sees where its focus is, gets out of every menu and of an embedded editor, and finds the focus back on the control that opened it

const FOCUSABLE = 'a[href], button, input, select, textarea, [tabindex]';

// a lesson's step, which embeds its examples
const LESSON = '/learn/lessons/one-light/';

const PHONE = { width: 375, height: 812 };

interface FocusState {
  focused: boolean;
  visible: boolean;
}

function focusStateOf(element: Element): FocusState {
  const style = getComputedStyle(element);
  const outlined = style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0 && !/^rgba\(.*,\s*0\)$|^transparent$/.test(style.outlineColor);
  return {
    focused: element === element.ownerDocument.activeElement,
    visible: element.matches(':focus-visible') && (outlined || style.boxShadow !== 'none'),
  };
}

// what the focus is on, said in a failure's message
async function describeFocus(page: Page): Promise<string> {
  return page.evaluate(() => {
    const active = document.activeElement;
    if (!(active instanceof HTMLElement) || active === document.body) {
      return 'the page';
    }
    const name = active.getAttribute('aria-label') ?? active.getAttribute('title') ?? active.innerText.trim().slice(0, 60);
    return `${active.tagName.toLowerCase()} "${name}"`;
  });
}

function searchOf(page: Page): Locator {
  return page.locator('input[aria-label="Search"]');
}

// the search loads its index on its first focus and takes the focus back once it has it, so the walk waits on the search until then
async function press(page: Page, key = 'Tab'): Promise<void> {
  await page.keyboard.press(key);
  const search = searchOf(page);
  if (await search.count() > 0 && await search.evaluate((element) => {
    return element === document.activeElement;
  })) {
    await expect.poll(async () => {
      return search.getAttribute('role');
    }).toBe('combobox');
  }
}

async function stopAt(control: Locator, what: string): Promise<void> {
  const state = await control.evaluate(focusStateOf);
  expect(state, `the focus is on ${what} and shows`).toEqual({ focused: true, visible: true });
}

async function isFocused(control: Locator): Promise<boolean> {
  return control.evaluate((element) => {
    return element === element.ownerDocument.activeElement;
  });
}

// presses Tab until the control has the focus, and fails naming the stops passed when it does not within the limit
async function tabTo(page: Page, control: Locator, what: string, limit = 60): Promise<void> {
  const passed: string[] = [];
  for (let presses = 0; presses < limit; presses++) {
    await press(page);
    if (await isFocused(control)) {
      await stopAt(control, what);
      return;
    }
    passed.push(await describeFocus(page));
  }
  throw new Error(`Tab did not reach ${what} in ${limit} presses, passing ${passed.join(', ')}`);
}

// marks every control the page shows that Tab can reach, and counts them
async function markControls(page: Page): Promise<number> {
  return page.evaluate((selector) => {
    const controls = [...document.querySelectorAll<HTMLElement>(selector)].filter((element) => {
      const rectangle = element.getBoundingClientRect();
      return element.tabIndex >= 0
        && !element.matches(':disabled')
        && element.closest('[hidden], [inert]') === null
        && rectangle.width > 0
        && rectangle.height > 0
        && getComputedStyle(element).visibility !== 'hidden';
    });
    for (const element of controls) {
      element.dataset.walkControl = '';
    }
    return controls.length;
  }, FOCUSABLE);
}

interface Reach {
  missed: string[];
  focusHidden: string[];
  replaced: number;
}

// Tab from the top of the page through all of it and round again, which no control may stop; a control the page shows only later, as the theme's button back to the top while it scrolls up, is not among them
async function walkWhole(page: Page): Promise<Reach> {
  await page.waitForLoadState('networkidle');
  const controls = await markControls(page);
  expect(controls).toBeGreaterThan(5);
  for (let pressed = 0; pressed < controls + 20; pressed++) {
    await press(page);
    const active = (await page.evaluateHandle(() => {
      return document.activeElement === document.body ? null : document.activeElement;
    })).asElement();
    if (active !== null) {
      const { visible } = await active.evaluate(focusStateOf);
      await active.evaluate((element, shown) => {
        if (element instanceof HTMLElement && element.dataset.walkSeen !== 'hidden') {
          element.dataset.walkSeen = shown ? 'shown' : 'hidden';
        }
      }, visible);
    }
  }
  return page.evaluate((total) => {
    const all = [...document.querySelectorAll('*')];
    const marked = [...document.querySelectorAll<HTMLElement>('[data-walk-control]')];
    const named = (element: HTMLElement): string => {
      const name = element.getAttribute('aria-label') ?? element.innerText.trim().split('\n')[0]?.slice(0, 40) ?? '';
      return `${all.indexOf(element)} ${element.tagName.toLowerCase()} "${name}"`;
    };
    return {
      missed: marked.filter((element) => {
        return element.dataset.walkSeen === undefined;
      }).map(named),
      focusHidden: marked.filter((element) => {
        return element.dataset.walkSeen === 'hidden';
      }).map(named),
      replaced: total - marked.length,
    };
  }, controls);
}

function header(page: Page) {
  return {
    skip: page.getByRole('link', { name: 'Skip to main content' }),
    brand: page.locator('.site-header-brand'),
    section: (label: string): Locator => {
      return page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: label, exact: true });
    },
    partsOfLearn: page.getByRole('button', { name: 'Parts of Learn' }),
    learnMenu: page.locator('.site-menu'),
    search: searchOf(page),
    source: page.locator('.site-header-end').getByRole('link', { name: 'Source' }),
    live: page.locator('.site-header-end').getByRole('link', { name: 'Open Live' }),
    theme: (label: string): Locator => {
      return page.getByRole('group', { name: 'Theme' }).getByRole('button', { name: label, exact: true });
    },
    phoneMenu: page.locator('.site-header-phone-menu'),
  };
}

describe('the keyboard', () => {
  test('walks the first page\'s header in order and reaches every control of the page with its focus shown', async ({ walk }) => {
    const page = await walk.open('/');
    const { skip, brand, section, partsOfLearn, search, source, live, theme } = header(page);
    const order: [Locator, string][] = [
      [skip, 'the link that skips to the content'],
      [brand, 'the brand'],
      ...sections.flatMap((entry): [Locator, string][] => {
        return entry.label === 'Learn' ? [[section(entry.label), entry.label], [partsOfLearn, 'the button of Learn\'s parts']] : [[section(entry.label), entry.label]];
      }),
      [search, 'the search'],
      [source, 'the link to the source'],
      [live, 'the link that opens live'],
      [theme('System'), 'the system theme'],
      [theme('Light'), 'the light theme'],
      [theme('Dark'), 'the dark theme'],
    ];
    for (const [control, what] of order) {
      await press(page);
      await stopAt(control, what);
    }

    await page.reload();
    expect(await walkWhole(page)).toEqual({ missed: [], focusHidden: [], replaced: 0 });
  });

  // a known gap of @easyops-cn/docusaurus-search-local: its search bar remembers a first focus and focuses itself once its index has loaded, wherever the focus has gone meanwhile
  test.fails('leaves the search before its index has loaded, and the search takes the focus back once it has, a defect of the search package', async ({ walk }) => {
    let releaseIndex = (): void => {};
    const indexHeld = new Promise<void>((released) => {
      releaseIndex = released;
    });
    const page = await walk.open('/');
    await page.context().route(/search-index.*\.json$/, async (route) => {
      await indexHeld;
      await route.continue();
    });
    const { search, source } = header(page);
    await search.focus();
    await page.keyboard.press('Tab');
    const left = await isFocused(source);
    releaseIndex();
    await expect.poll(async () => {
      return search.getAttribute('role');
    }).toBe('combobox');

    expect({ left, kept: await isFocused(source) }).toEqual({ left: true, kept: true });
  });

  test('opens Learn\'s menu, walks its parts and closes it with Escape on its button', async ({ walk }) => {
    const page = await walk.open('/');
    const { partsOfLearn, learnMenu } = header(page);
    await tabTo(page, partsOfLearn, 'the button of Learn\'s parts');
    await press(page, 'Enter');
    expect(await partsOfLearn.getAttribute('aria-expanded')).toBe('true');

    for (const part of learnParts) {
      await press(page);
      await stopAt(learnMenu.getByRole('link', { name: part.label, exact: true }), `the menu's ${part.label}`);
    }
    await press(page, 'Escape');

    expect(await learnMenu.isHidden()).toBe(true);
    await stopAt(partsOfLearn, 'the button of Learn\'s parts');
  });

  test('searches by the keyboard and opens a result', async ({ walk }) => {
    const page = await walk.open('/');
    const { search } = header(page);
    const first = page.url();
    await tabTo(page, search, 'the search');
    await page.keyboard.type('trace');
    await expect.poll(async () => {
      return page.locator('[role="option"]').count();
    }).toBeGreaterThan(0);
    await press(page, 'ArrowDown');
    await expect.poll(async () => {
      return page.locator('[role="option"][aria-selected="true"]').count();
    }).toBe(1);
    await press(page, 'Enter');

    await expect.poll(() => {
      return page.url();
    }).not.toBe(first);
    expect(new URL(page.url()).pathname).not.toMatch(/\/search\/?$/);
  });

  test('switches to the dark theme and keeps the focus on the switch', async ({ walk }) => {
    const page = await walk.open('/');
    const dark = header(page).theme('Dark');
    await tabTo(page, dark, 'the dark theme');
    await press(page, 'Enter');

    await expect.poll(async () => {
      return page.evaluate(() => {
        return document.documentElement.dataset.theme;
      });
    }).toBe('dark');
    expect(await dark.getAttribute('aria-pressed')).toBe('true');
    await stopAt(dark, 'the dark theme');
  });

  test('reaches a doc page\'s sidebar and its own links, and every control of the page with its focus shown', async ({ walk }) => {
    const page = await walk.open(LESSON);
    const sidebar = page.getByRole('navigation', { name: 'Docs sidebar' });
    await tabTo(page, sidebar.getByRole('link').first(), 'the sidebar\'s first link');
    await tabTo(page, sidebar.locator('a[aria-current="page"]'), 'the sidebar\'s link of the page');
    await tabTo(page, page.locator('article .markdown a[href]:not(.hash-link)').first(), 'the page\'s first link of its own');
    await tabTo(page, page.getByRole('navigation', { name: 'Docs pages' }).getByRole('link').last(), 'the link to the next page', 120);

    await page.reload();
    expect(await walkWhole(page)).toEqual({ missed: [], focusHidden: [], replaced: 0 });
  });

  test('opens a lesson\'s example into its editor, leaves the editor by Tab and finds the focus back on its control once it closes', async ({ walk }) => {
    const page = await walk.open(LESSON);
    const run = page.getByRole('button', { name: 'Run', exact: true }).first();
    await tabTo(page, run, 'the example\'s control', 120);
    await press(page, 'Enter');

    const frame = page.locator('article iframe').first().contentFrame();
    const editorFocused = async (): Promise<boolean> => {
      return frame.locator('.monaco-editor').first().evaluate((editor) => {
        return editor.ownerDocument.hasFocus() && editor.contains(editor.ownerDocument.activeElement);
      }).catch(() => {
        return false;
      });
    };
    await expect.poll(async () => {
      return frame.locator('.monaco-editor .view-lines').first().innerText().catch(() => {
        return '';
      });
    }, { timeout: 120_000 }).toContain('package');
    await expect.poll(editorFocused, { message: 'the opened example has the focus in its editor' }).toBe(true);
    await press(page);
    expect(await editorFocused(), 'Tab leaves the embedded editor').toBe(false);
    await stopAt(frame.getByRole('button', { name: 'Verify all', exact: true }), 'the embedded editor\'s Verify all');
    const close = page.getByRole('button', { name: 'Close the editor' });
    await tabTo(page, close, 'the control that closes the editor', 10);
    await press(page, 'Enter');

    await expect.poll(async () => {
      return page.locator('article iframe').count();
    }).toBe(0);
    await stopAt(run, 'the example\'s control');
  });

  test('opens the header\'s menu on a phone, and leaves it by Escape and by Tab', async ({ walk }) => {
    const page = await walk.open('/', PHONE);
    const { phoneMenu } = header(page);
    const shown = page.locator('.navbar-sidebar--show');
    const menu = page.locator('.navbar-sidebar');
    await tabTo(page, phoneMenu, 'the header\'s menu', 10);
    await press(page, 'Enter');
    await expect.poll(async () => {
      return shown.count();
    }).toBe(1);
    await tabTo(page, menu.locator('.menu__link', { hasText: /^Learn$/ }), 'the menu\'s Learn', 10);
    await press(page, 'Escape');

    await expect.poll(async () => {
      return shown.count();
    }).toBe(0);
    await stopAt(phoneMenu, 'the header\'s menu');

    await press(page, 'Enter');
    await expect.poll(async () => {
      return shown.count();
    }).toBe(1);
    await tabTo(page, menu.locator('a', { hasText: 'Open Live' }), 'the menu\'s last entry', 20);
    await press(page);

    await expect.poll(async () => {
      return shown.count();
    }).toBe(0);
  });
});
