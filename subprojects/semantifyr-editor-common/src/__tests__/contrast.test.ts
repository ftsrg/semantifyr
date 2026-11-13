/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs';
import { describe, expect, test } from 'vitest';

type Look = 'light' | 'dark';
type Color = readonly [number, number, number, number];

const TOKENS = readFileSync(new URL('../look/tokens.css', import.meta.url), 'utf8');

function declarationsOf(selector: string): Map<string, string> {
  const start = TOKENS.indexOf(`${selector} {`);
  if (start < 0) {
    throw new Error(`tokens.css has no ${selector} block`);
  }
  const body = TOKENS.slice(start, TOKENS.indexOf('}', start));
  return new Map([...body.matchAll(/--([\w-]+):\s*([^;]+);/g)].map((match) => {
    return [match[1]!, match[2]!.trim()];
  }));
}

const LIGHT = declarationsOf(':root');
const LOOKS: Readonly<Record<Look, Map<string, string>>> = {
  light: LIGHT,
  dark: new Map([...LIGHT, ...declarationsOf(':root[data-theme="dark"]')]),
};

function parseColor(value: string): Color {
  const hex = /^#([0-9a-f]{6})$/i.exec(value);
  if (hex) {
    const number = parseInt(hex[1]!, 16);
    return [number >> 16, (number >> 8) & 255, number & 255, 1];
  }
  const rgba = /^rgba\(([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)\)$/.exec(value);
  if (rgba) {
    return [Number(rgba[1]), Number(rgba[2]), Number(rgba[3]), Number(rgba[4])];
  }
  throw new Error(`${value} is not a colour this test reads`);
}

function over(top: Color, base: Color): Color {
  const mix = (index: 0 | 1 | 2): number => {
    return top[index] * top[3] + base[index] * (1 - top[3]);
  };
  return [mix(0), mix(1), mix(2), 1];
}

function luminance(color: Color): number {
  const channel = (value: number): number => {
    const scaled = value / 255;
    return scaled <= 0.04045 ? scaled / 12.92 : ((scaled + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(color[0]) + 0.7152 * channel(color[1]) + 0.0722 * channel(color[2]);
}

function contrast(first: Color, second: Color): number {
  const [lighter, darker] = [luminance(first), luminance(second)].sort((a, b) => {
    return b - a;
  });
  return (lighter! + 0.05) / (darker! + 0.05);
}

// a surface is a token, or a translucent token laid over another
type Surface = string | readonly [string, string];

interface Use {
  foregrounds: readonly string[];
  surfaces: readonly Surface[];
  minimum: number;
}

const OVER_PANEL = ['surface-selected', 'surface-panel'] as const;
const OVER_EDITOR = ['surface-selected', 'surface-editor'] as const;
const SELECTED_MENU_ENTRY = ['surface-selected', 'surface-overlay'] as const;

const TEXT_SURFACES: readonly Surface[] = [
  'surface-chrome',
  'surface-editor',
  'surface-readonly',
  'surface-panel',
  'surface-raised',
  'surface-overlay',
  'surface-inset',
  OVER_PANEL,
  OVER_EDITOR,
];

const TEXT = 4.5;
const NON_TEXT = 3;

const USES: readonly Use[] = [
  {
    foregrounds: [
      'text-primary',
      'text-secondary',
      'text-tertiary',
      'accent-ink',
    ],
    surfaces: TEXT_SURFACES,
    minimum: TEXT,
  },
  {
    foregrounds: [
      'text-primary',
      'text-tertiary',
    ],
    surfaces: [
      SELECTED_MENU_ENTRY,
    ],
    minimum: TEXT,
  },
  {
    foregrounds: [
      'attention',
      'problem',
      'verdict-passed',
      'verdict-failed',
      'verdict-inconclusive',
      'verdict-neutral',
    ],
    surfaces: [
      'surface-chrome',
      'surface-editor',
      'surface-panel',
      'surface-raised',
      'surface-overlay',
      OVER_PANEL,
      OVER_EDITOR,
    ],
    minimum: TEXT,
  },
  {
    foregrounds: [
      'language-sysml',
      'language-gamma',
      'language-oxsts',
    ],
    surfaces: [
      'surface-chrome',
      'surface-editor',
      'surface-panel',
      'surface-raised',
      'surface-overlay',
    ],
    minimum: TEXT,
  },
  {
    foregrounds: [
      'syntax-keyword',
      'syntax-type',
      'syntax-name',
      'syntax-string',
      'syntax-number',
      'syntax-comment',
      'syntax-operator',
      'syntax-punctuation',
      'syntax-function',
      'editor-gutter-text',
      'editor-gutter-active',
    ],
    surfaces: [
      'surface-readonly',
      'surface-editor',
      ['editor-step-highlight', 'surface-editor'],
    ],
    minimum: TEXT,
  },
  {
    foregrounds: [
      'accent-text',
    ],
    surfaces: [
      'accent',
      'accent-hover',
    ],
    minimum: TEXT,
  },
  {
    foregrounds: [
      'verdict-glyph-on-disc',
    ],
    surfaces: [
      'verdict-passed',
      'verdict-failed',
    ],
    minimum: NON_TEXT,
  },
  {
    foregrounds: [
      'accent-ink',
    ],
    surfaces: [
      SELECTED_MENU_ENTRY,
    ],
    minimum: NON_TEXT,
  },
];

// pairs the contrast note gives no value for; each is the owner's to decide
const UNDECIDED: readonly string[] = [
  'dark accent-text on accent-hover',
  'dark text-tertiary on surface-selected over surface-overlay',
];

interface Pair {
  name: string;
  ratio: number;
  minimum: number;
}

function colorOf(look: Look, token: string): Color {
  const value = LOOKS[look].get(token);
  if (value === undefined) {
    throw new Error(`tokens.css declares no --${token}`);
  }
  return parseColor(value);
}

function surfaceColor(look: Look, surface: Surface): Color {
  return typeof surface === 'string' ? colorOf(look, surface) : over(colorOf(look, surface[0]), colorOf(look, surface[1]));
}

function surfaceName(surface: Surface): string {
  return typeof surface === 'string' ? surface : `${surface[0]} over ${surface[1]}`;
}

const PAIRS: readonly Pair[] = (['light', 'dark'] as const).flatMap((look) => {
  return USES.flatMap((use) => {
    return use.foregrounds.flatMap((foreground) => {
      return use.surfaces.map((surface) => {
        return {
          name: `${look} ${foreground} on ${surfaceName(surface)}`,
          ratio: contrast(colorOf(look, foreground), surfaceColor(look, surface)),
          minimum: use.minimum,
        };
      });
    });
  });
});

function failing(pairs: readonly Pair[]): string[] {
  return pairs.filter((pair) => {
    return pair.ratio < pair.minimum;
  }).map((pair) => {
    return `${pair.name}: ${pair.ratio.toFixed(2)}, needs ${pair.minimum}`;
  });
}

describe('the tokens meet level AA', () => {
  test('every text reaches 4.5 and every mark and ring 3 against the surface it sits on', () => {
    expect(failing(PAIRS.filter((pair) => {
      return !UNDECIDED.includes(pair.name);
    }))).toEqual([]);
  });

  test('names only pairs it checks as undecided', () => {
    const names = PAIRS.map((pair) => {
      return pair.name;
    });
    expect(UNDECIDED.filter((name) => {
      return !names.includes(name);
    })).toEqual([]);
  });

  test.fails('the pairs the contrast note gives no value for pass too', () => {
    expect(failing(PAIRS.filter((pair) => {
      return UNDECIDED.includes(pair.name);
    }))).toEqual([]);
  });
});
