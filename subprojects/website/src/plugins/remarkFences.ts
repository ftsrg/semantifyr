/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Code, Parent } from 'mdast';

const HIDE_START = '// hide-start';
const HIDE_END = '// hide-end';

interface InfoWords {
  hidden: boolean;
  continues: boolean;
  issue: string | null;
}

function infoWordsOf(code: Code, where: string): InfoWords {
  const words: InfoWords = { hidden: false, continues: false, issue: null };
  for (const word of (code.meta ?? '').split(/\s+/)) {
    if (word === '') {
      continue;
    }
    const separator = word.indexOf('=');
    const key = separator < 0 ? word : word.slice(0, separator);
    const value = separator < 0 ? null : word.slice(separator + 1);
    if (key === 'hidden' && value === null) {
      words.hidden = true;
    } else if (key === 'continue' && value === null) {
      words.continues = true;
    } else if (key === 'issue' && value !== null && value !== '') {
      words.issue = value;
    } else {
      throw new Error(`The oxsts fence has an unknown word ${word}, ${where}`);
    }
  }
  return words;
}

interface ModelLine {
  text: string;
  pageLine: number;
}

interface Lines {
  own: ModelLine[];
  shown: number[];
}

function linesOf(text: string, openingLine: number, where: string): Lines {
  const own: ModelLine[] = [];
  const shown: number[] = [];
  let hiding = false;
  for (const [index, line] of text.split('\n').entries()) {
    const marker = line.trim();
    if (marker === HIDE_START || marker === HIDE_END) {
      if (hiding === (marker === HIDE_START)) {
        throw new Error(`The oxsts fence has an unpaired ${marker}, ${where}`);
      }
      hiding = marker === HIDE_START;
      continue;
    }
    if (!hiding) {
      shown.push(own.length);
    }
    own.push({ text: line, pageLine: openingLine + 1 + index });
  }
  if (hiding) {
    throw new Error(`The oxsts fence has an unpaired ${HIDE_START}, ${where}`);
  }
  return { own, shown };
}

interface Fence {
  openingLine: number;
  model: string;
  pageLines: number[];
  continued: boolean;
  issue: string | null;
}

class Page {
  readonly fences: Fence[] = [];

  private previous: ModelLine[] | null = null;

  fence(code: Code): Code[] {
    const openingLine = code.position?.start.line ?? 0;
    const where = `line ${code.position?.start.line ?? '?'}`;
    const words = infoWordsOf(code, where);
    const { own, shown } = linesOf(code.value, openingLine, where);
    let prefix: ModelLine[] = [];
    if (words.continues) {
      if (this.previous === null) {
        throw new Error(`The oxsts fence continues no earlier fence, ${where}`);
      }
      prefix = [...this.previous, { text: '', pageLine: openingLine }];
    }
    const model = [...prefix, ...own];
    this.previous = model;
    const modelText = model.map((line) => {
      return line.text;
    }).join('\n');
    this.fences.push({
      openingLine,
      model: modelText,
      pageLines: model.map((line) => {
        return line.pageLine;
      }),
      continued: words.continues,
      issue: words.issue,
    });
    if (words.hidden) {
      return [];
    }
    const visible = shown.filter((index) => {
      return own[index]?.text.trim() !== '';
    });
    const first = visible[0] ?? 0;
    const last = visible[visible.length - 1] ?? -1;
    const shownLines = shown.filter((index) => {
      return index >= first && index <= last;
    }).map((index) => {
      return own[index]?.text ?? '';
    });
    // the members of a hidden class are shown without its indentation
    const indentation = Math.min(...shownLines.filter((line) => {
      return line.trim() !== '';
    }).map((line) => {
      return line.length - line.trimStart().length;
    }));
    const shownText = shownLines.map((line) => {
      return line.slice(Number.isFinite(indentation) ? indentation : 0);
    }).join('\n');
    return [{ ...code, value: shownText, meta: null }];
  }
}

function transform(parent: Parent, page: Page): void {
  parent.children = parent.children.flatMap((child) => {
    if (child.type === 'code' && child.lang === 'oxsts') {
      return page.fence(child);
    }
    if ('children' in child) {
      transform(child, page);
    }
    return [child];
  });
}

export function fencesOf(root: Parent): Fence[] {
  const page = new Page();
  transform(root, page);
  return page.fences;
}

export default function remarkFences(): (root: Parent) => void {
  return (root) => {
    transform(root, new Page());
  };
}
