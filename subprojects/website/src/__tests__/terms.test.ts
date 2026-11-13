/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { terms } from '@semantifyr/editor-common/terms';
import { describe, expect, it } from 'vitest';

const REPOSITORY = fileURLToPath(new URL('../../../../', import.meta.url));

// the pages and components a reader meets; the tests and their fixtures are not read
const SOURCES = [
  'subprojects/website/src/docs',
  'subprojects/website/src/pages',
  'subprojects/website/src/components',
  'subprojects/website/src/theme',
  'subprojects/semantifyr-live/frontend/src',
  'subprojects/semantifyr-live/frontend/index.html',
];

const SKIPPED = /(^|\/)(__[a-z]+__|snippets|vscode)(\/|$)|testBrowser\.ts$/;

function filesUnder(path: string): string[] {
  const absolute = join(REPOSITORY, path);
  if (statSync(absolute).isFile()) {
    return [path];
  }
  return readdirSync(absolute, { recursive: true, withFileTypes: true })
    .filter((entry) => {
      return entry.isFile() && /\.(md|mdx|ts|tsx|html)$/.test(entry.name);
    })
    .map((entry) => {
      return relative(REPOSITORY, join(entry.parentPath, entry.name));
    })
    .filter((file) => {
      return !SKIPPED.test(file);
    });
}

interface Text {
  line: number;
  text: string;
}

// the strings and the JSX text of a source file, without its comments and module names
function textsOfCode(source: string): Text[] {
  const texts: Text[] = [];
  let line = 1;
  let index = 0;
  let code = '';
  let codeLine = 1;
  const flushCode = (): void => {
    for (const match of code.matchAll(/>([^<>{}();=]*[A-Za-z][^<>{}();=]*)</g)) {
      texts.push({ line: codeLine + (code.slice(0, match.index).match(/\n/g)?.length ?? 0), text: match[1] ?? '' });
    }
    code = '';
  };
  while (index < source.length) {
    const character = source[index];
    const next = source[index + 1];
    if (character === '/' && next === '/') {
      flushCode();
      while (index < source.length && source[index] !== '\n') {
        index++;
      }
      continue;
    }
    if (character === '/' && next === '*') {
      flushCode();
      const end = source.indexOf('*/', index + 2);
      const stop = end === -1 ? source.length : end + 2;
      line += source.slice(index, stop).match(/\n/g)?.length ?? 0;
      index = stop;
      codeLine = line;
      continue;
    }
    if (character === '\'' || character === '"' || character === '`') {
      flushCode();
      const start = line;
      let text = '';
      index++;
      while (index < source.length && source[index] !== character) {
        if (source[index] === '\\') {
          text += source[index + 1] ?? '';
          index += 2;
          continue;
        }
        if (source[index] === '\n') {
          line++;
        }
        text += source[index] ?? '';
        index++;
      }
      index++;
      codeLine = line;
      // a module name or a class name is no text: user texts have a space or a capital
      if (/\s|[A-Z]/.test(text) && !/^\.{0,2}\//.test(text)) {
        texts.push({ line: start, text });
      }
      continue;
    }
    if (character === '\n') {
      line++;
    }
    code += character ?? '';
    index++;
  }
  flushCode();
  return texts;
}

function textsOfPage(source: string): Text[] {
  return source.split('\n').map((text, index) => {
    return { line: index + 1, text };
  });
}

function rejectedPattern(word: string): RegExp {
  const words = word.split(/\s+/).map((part) => {
    return part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  });
  return new RegExp(`(?<![\\p{L}\\p{N}])${words.join('\\s+')}(?![\\p{L}\\p{N}])`, 'iu');
}

const RULES = Object.values(terms).flatMap((term) => {
  return term.rejected.map((word) => {
    return { word, term: term.term, pattern: rejectedPattern(word), explainedOn: term.explainedOn };
  });
});

describe('the terms', () => {
  const files = SOURCES.flatMap(filesUnder);

  it('reads the pages and the components of the website and of live', () => {
    expect(files.some((file) => {
      return file.endsWith('.md');
    })).toBe(true);
    expect(files.some((file) => {
      return file.startsWith('subprojects/semantifyr-live/frontend/src/shells/');
    })).toBe(true);
  });

  it('finds no rejected word in a page or a component', () => {
    const found = files.flatMap((file) => {
      const source = readFileSync(join(REPOSITORY, file), 'utf-8');
      const texts = /\.(md|mdx)$/.test(file) ? textsOfPage(source) : textsOfCode(source);
      return texts.flatMap(({ line, text }) => {
        return RULES
          .filter((rule) => {
            return rule.explainedOn !== file && rule.pattern.test(text);
          })
          .map((rule) => {
            return `${file}:${line}: "${rule.word}", say "${rule.term}"`;
          });
      });
    });
    expect(found).toEqual([]);
  });

  it('explains a rejected word on the one page that may use it', () => {
    for (const term of Object.values(terms)) {
      if (term.explainedOn !== undefined) {
        const page = readFileSync(join(REPOSITORY, term.explainedOn), 'utf-8');
        expect(term.rejected.some((word) => {
          return rejectedPattern(word).test(page);
        }), `${term.explainedOn} explains what "${term.term}" replaces`).toBe(true);
      }
    }
  });
});
