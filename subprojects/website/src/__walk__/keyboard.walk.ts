/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ElementHandle, Locator, Page } from 'playwright';
import { describe, expect } from 'vitest';
import { learnParts, sections } from '../map';
import { test } from './harness';

// Goal: a keyboard alone reaches every control of the site, sees where its focus is, gets out of every menu and of an embedded editor, and finds the focus back on the control that opened it

const FOCUSABLE = [
  'a[href]',
  'area[href]',
  'button',
  'input',
  'select',
  'textarea',
  'summary',
  'iframe',
  'audio[controls]',
  'video[controls]',
  '[contenteditable]:not([contenteditable="false"])',
  '[tabindex]',
].join(', ');

// a lesson's step, which embeds its examples
const LESSON = '/learn/lessons/one-light/';

const PHONE = { width: 375, height: 812 };

interface FocusState {
  focused: boolean;
  visible: boolean;
}

// a focus shows when the control looks otherwise focused than its unfocused twin, by its outline or its ring, since a pressed button has a ring of its own
function focusStateOf(element: Element): FocusState {
  const ringOf = (style: CSSStyleDeclaration): string => {
    const outlined = style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0 && !/^rgba\(.*,\s*0\)$|^transparent$/.test(style.outlineColor);
    return `${outlined ? `${style.outlineStyle} ${style.outlineWidth} ${style.outlineColor} ${style.outlineOffset}` : 'none'} ${style.boxShadow}`;
  };
  const owner = element.ownerDocument;
  const focusedRing = ringOf(getComputedStyle(element));
  const twin = element.cloneNode(false) as Element;
  twin.removeAttribute('id');
  element.after(twin);
  const unfocusedRing = ringOf(getComputedStyle(twin));
  twin.remove();
  return {
    focused: element === owner.activeElement && owner.hasFocus(),
    visible: element.matches(':focus-visible') && focusedRing !== unfocusedRing,
  };
}

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
  return page.getByRole('textbox', { name: 'Search', exact: true }).or(page.getByRole('combobox', { name: 'Search', exact: true }));
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

// a control the page shows only later, as the theme's button back to the top while the page scrolls up, is not marked, and a Tab landing on it fails the walk
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

interface Landing {
  described: string;
  marked: boolean;
  shown: boolean;
  frame: boolean;
}

async function landingOf(active: ElementHandle<Element>): Promise<Landing> {
  return active.evaluate((element) => {
    const html = element as HTMLElement;
    const rectangle = element.getBoundingClientRect();
    const name = element.getAttribute('aria-label') ?? html.innerText.trim().split('\n')[0]?.slice(0, 40) ?? '';
    return {
      described: `${element.tagName.toLowerCase()} "${name}"`,
      marked: html.dataset.walkControl !== undefined,
      shown: rectangle.width > 0
        && rectangle.height > 0
        && getComputedStyle(element).visibility !== 'hidden'
        && element.closest('[hidden], [inert]') === null,
      frame: element.tagName === 'IFRAME',
    };
  });
}

interface Reach {
  missed: string[];
  focusHidden: string[];
  replaced: number;
}

// Tab from the top of the page to its end, where the focus leaves the page; every Tab lands on a control the page shows, or the walk fails naming where it landed
async function walkWhole(page: Page): Promise<Reach> {
  await page.waitForLoadState('networkidle');
  const controls = await markControls(page);
  expect(controls).toBeGreaterThan(5);
  let ended = false;
  for (let pressed = 0; pressed < controls + 5 && !ended; pressed++) {
    await press(page);
    const active = (await page.evaluateHandle(() => {
      return document.activeElement === document.body ? null : document.activeElement;
    })).asElement();
    if (active === null) {
      expect(pressed, 'Tab leaves the page only after its last control').toBeGreaterThan(0);
      ended = true;
      continue;
    }
    const landing = await landingOf(active);
    if (!landing.marked || !landing.shown || landing.frame) {
      throw new Error(`Tab ${pressed + 1} landed on ${landing.described}, which is no control the page shows${landing.frame ? ', a frame the walk does not look into' : ''}`);
    }
    const { visible } = await active.evaluate(focusStateOf);
    const seenBefore = await active.evaluate((element, shown) => {
      const html = element as HTMLElement;
      const before = html.dataset.walkSeen !== undefined;
      if (html.dataset.walkSeen !== 'hidden') {
        html.dataset.walkSeen = shown ? 'shown' : 'hidden';
      }
      return before;
    }, visible);
    ended = seenBefore;
  }
  expect(ended, 'Tab comes to the end of the page').toBe(true);
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
  const navigation = page.getByRole('navigation', { name: 'Main' });
  return {
    skip: page.getByRole('link', { name: 'Skip to main content' }),
    brand: navigation.getByRole('link', { name: 'Semantifyr Docs', exact: true }),
    section: (label: string): Locator => {
      return navigation.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: label, exact: true });
    },
    partsOfLearn: navigation.getByRole('button', { name: 'Parts of Learn' }),
    learnMenu: page.locator('.site-menu'),
    search: searchOf(page),
    source: navigation.getByRole('link', { name: 'Source', exact: true }),
    live: navigation.getByRole('link', { name: 'Open Live', exact: true }),
    theme: (label: string): Locator => {
      return navigation.getByRole('group', { name: 'Theme' }).getByRole('button', { name: label, exact: true });
    },
    phoneMenu: navigation.getByRole('button', { name: /^Section / }),
  };
}

// the theme's phone menu slides in, and a Tab in its first frame, while it is still hidden, passes it by
async function phoneMenuShown(page: Page): Promise<void> {
  await expect.poll(async () => {
    return page.locator('.navbar-sidebar--show .navbar-sidebar').evaluate((element) => {
      return getComputedStyle(element).visibility === 'visible' && element.getAnimations().length === 0;
    }).catch(() => {
      return false;
    });
  }).toBe(true);
}

describe('the keyboard', () => {
  test('walks the first page\'s header in order and reaches every control of the page with its focus shown', async ({ walk }) => {
    const page = await walk.open('/');
    const { skip, brand, section, partsOfLearn, search, source, live, theme } = header(page);
    const order: [Locator, string][] = [
      [skip, 'the link that skips to the content'],
      [brand, 'the brand'],
      ...sections.flatMap((entry): [Locator, string][] => {
        if (entry.label === 'Learn') {
          return [
            [section(entry.label), entry.label],
            [partsOfLearn, 'the button of Learn\'s parts'],
          ];
        }
        return [
          [section(entry.label), entry.label],
        ];
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

  // a known gap of @easyops-cn/docusaurus-search-local: its search bar sets a flag on its first focus that no blur resets, and focuses itself once its index has loaded, wherever the focus has gone meanwhile; once the package mends it, the last assertion fails and turns
  test('leaves the search before its index has loaded, and the search takes the focus back once it has, a known gap of the search package', async ({ walk }) => {
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
    const passed: string[] = [];
    for (let presses = 0; presses < 10 && !await isFocused(search); presses++) {
      await page.keyboard.press('Tab');
      passed.push(await describeFocus(page));
    }
    expect(await isFocused(search), `Tab reaches the search, passing ${passed.join(', ')}`).toBe(true);
    await page.keyboard.press('Tab');
    await stopAt(source, 'the link to the source');
    releaseIndex();
    await expect.poll(async () => {
      return search.getAttribute('role');
    }).toBe('combobox');

    expect(await isFocused(source), 'the search took the focus back from where the reader tabbed to').toBe(false);
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

  test('searches by the keyboard and opens the result it selected', async ({ walk }) => {
    const page = await walk.open('/');
    const { search } = header(page);
    const first = page.url();
    await tabTo(page, search, 'the search');
    await page.keyboard.type('trace');
    await expect.poll(async () => {
      return page.getByRole('option').count();
    }).toBeGreaterThan(0);
    await press(page, 'ArrowDown');
    const selected = page.getByRole('option', { selected: true });
    await expect.poll(async () => {
      return selected.count();
    }).toBe(1);
    const selectedText = await selected.innerText();
    await press(page, 'Enter');

    await expect.poll(() => {
      return page.url();
    }).not.toBe(first);
    const title = await page.getByRole('heading', { level: 1 }).innerText();
    expect(selectedText, 'the opened page is the one the selected result names').toContain(title);
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
    const title = await page.getByRole('heading', { level: 1 }).innerText();
    const ownLink = await page.getByRole('article').evaluate((article) => {
      const link = [...article.querySelectorAll<HTMLAnchorElement>('.markdown a[href]')].find((candidate) => {
        return candidate.closest('nav, h1, h2, h3, h4, h5, h6') === null;
      });
      return link?.innerText.trim() ?? '';
    });
    expect(ownLink, 'the page has a link of its own in its text').not.toBe('');
    await tabTo(page, sidebar.getByRole('link').first(), 'the sidebar\'s first link');
    await tabTo(page, sidebar.getByRole('link', { name: title, exact: true }), 'the sidebar\'s link of the page');
    await tabTo(page, page.getByRole('article').getByRole('link', { name: ownLink, exact: true }).first(), 'the page\'s first link of its own');
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
    await stopAt(frame.getByRole('button', { name: 'Verify all', exact: true }), 'the embedded editor\'s Verify all, in a frame that has the focus');
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
    await tabTo(page, phoneMenu, 'the header\'s menu', 10);
    await press(page, 'Enter');
    await phoneMenuShown(page);
    await tabTo(page, page.getByRole('button', { name: 'Learn', exact: true }), 'the menu\'s Learn', 10);
    await press(page, 'Escape');

    await expect.poll(async () => {
      return shown.count();
    }).toBe(0);
    await stopAt(phoneMenu, 'the header\'s menu');

    await press(page, 'Enter');
    await phoneMenuShown(page);
    await tabTo(page, page.getByRole('link', { name: 'Open Live (opens in new tab)', exact: true }), 'the menu\'s last entry', 20);
    await press(page);

    await expect.poll(async () => {
      return shown.count();
    }).toBe(0);
    expect(await page.getByRole('main').evaluate((main) => {
      return main.contains(document.activeElement);
    }), `the focus has gone on to the page's content, not ${await describeFocus(page)}`).toBe(true);
    await stopAt(page.locator(':focus'), 'the page\'s first control');
  });
});
