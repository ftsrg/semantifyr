/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Code, Parent, RootContent } from 'mdast';
import type { MdxJsxAttribute, MdxJsxFlowElement } from 'mdast-util-mdx-jsx';
import type { MdxjsEsm } from 'mdast-util-mdxjs-esm';

const HIDE_START = '// hide-start';
const HIDE_END = '// hide-end';

const COMPONENT = 'SemantifyrFence';
const COMPONENT_PATH = '@site/src/components/Fence';
const VERIFICATION_CASE = /@VerificationCase\b/;

interface InfoWords {
  embed: 'run' | 'edit' | null;
  open: boolean;
  hidden: boolean;
  continues: string | null;
  checkpoint: string | null;
  issue: string | null;
}

// the previous fence of the page, or the fence that set the checkpoint
const PREVIOUS = '';

function infoWordsOf(code: Code, where: string): InfoWords {
  const words: InfoWords = { embed: null, open: false, hidden: false, continues: null, checkpoint: null, issue: null };
  for (const word of (code.meta ?? '').split(/\s+/)) {
    if (word === '') {
      continue;
    }
    const separator = word.indexOf('=');
    const key = separator < 0 ? word : word.slice(0, separator);
    const value = separator < 0 ? null : word.slice(separator + 1);
    if ((key === 'run' || key === 'edit') && value === null) {
      if (words.embed !== null) {
        throw new Error(`An oxsts fence is run or edited, not both, ${where}`);
      }
      words.embed = key;
    } else if (key === 'open' && value === null) {
      words.open = true;
    } else if (key === 'hidden' && value === null) {
      words.hidden = true;
    } else if (key === 'continue' && value !== '') {
      words.continues = value ?? PREVIOUS;
    } else if (key === 'checkpoint' && value !== null && value !== '') {
      words.checkpoint = value;
    } else if (key === 'issue' && value !== null && value !== '') {
      words.issue = value;
    } else {
      throw new Error(`The oxsts fence has an unknown word ${word}, ${where}`);
    }
  }
  if (words.hidden && (words.embed !== null || words.open)) {
    throw new Error(`A hidden oxsts fence is neither embedded nor opened, ${where}`);
  }
  return words;
}

interface ModelLine {
  text: string;
  pageLine: number;
}

interface Lines {
  // the fence's lines without the markers
  own: ModelLine[];
  // the indices in own of the lines the reader sees
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

function attribute(name: string, value: string | null): MdxJsxAttribute {
  return { type: 'mdxJsxAttribute', name, value };
}

function importNode(): MdxjsEsm {
  const source = JSON.stringify(COMPONENT_PATH);
  return {
    type: 'mdxjsEsm',
    value: `import ${COMPONENT} from ${source}`,
    data: {
      estree: {
        type: 'Program',
        sourceType: 'module',
        body: [
          {
            type: 'ImportDeclaration',
            specifiers: [
              {
                type: 'ImportDefaultSpecifier',
                local: { type: 'Identifier', name: COMPONENT },
              },
            ],
            attributes: [],
            source: { type: 'Literal', value: COMPONENT_PATH, raw: source },
          },
        ],
      },
    },
  };
}

interface Fence {
  openingLine: number;
  model: string;
  // a continued fence's blank line is on its opening line
  pageLines: number[];
  continued: boolean;
  issue: string | null;
}

class Page {
  needsImport = false;

  readonly fences: Fence[] = [];

  private previous: ModelLine[] | null = null;

  private readonly checkpoints = new Map<string, ModelLine[]>();

  // a continued fence's model is the model it continues, a blank line and its own lines
  fence(code: Code): RootContent[] {
    const openingLine = code.position?.start.line ?? 0;
    const where = `line ${code.position?.start.line ?? '?'}`;
    const words = infoWordsOf(code, where);
    const { own, shown } = linesOf(code.value, openingLine, where);
    let prefix: ModelLine[] = [];
    if (words.continues !== null) {
      const continued = words.continues === PREVIOUS ? this.previous : this.checkpoints.get(words.continues) ?? null;
      if (continued === null) {
        throw new Error(`The oxsts fence continues ${words.continues === PREVIOUS ? 'no earlier fence' : `no checkpoint ${words.continues}`}, ${where}`);
      }
      prefix = [...continued, { text: '', pageLine: openingLine }];
    }
    const model = [...prefix, ...own];
    this.previous = model;
    if (words.checkpoint !== null) {
      if (this.checkpoints.has(words.checkpoint)) {
        throw new Error(`The checkpoint ${words.checkpoint} is set twice, ${where}`);
      }
      this.checkpoints.set(words.checkpoint, model);
    }
    const modelText = model.map((line) => {
      return line.text;
    }).join('\n');
    this.fences.push({
      openingLine,
      model: modelText,
      pageLines: model.map((line) => {
        return line.pageLine;
      }),
      continued: words.continues !== null,
      issue: words.issue,
    });
    if (words.hidden) {
      return [];
    }
    if (words.embed === 'run' && !VERIFICATION_CASE.test(modelText)) {
      throw new Error(`A fence that is run holds a verification case, ${where}`);
    }
    if (words.embed === 'edit' && VERIFICATION_CASE.test(modelText)) {
      throw new Error(`A fence with a verification case is run, not edited, ${where}`);
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
    // a member in a hidden class is indented in the model as a file has it, and shown without the class's indentation
    const indentation = Math.min(...shownLines.filter((line) => {
      return line.trim() !== '';
    }).map((line) => {
      return line.length - line.trimStart().length;
    }));
    const shownText = shownLines.map((line) => {
      return line.slice(Number.isFinite(indentation) ? indentation : 0);
    }).join('\n');
    if (words.embed === null && !words.open && modelText === shownText) {
      return [code];
    }
    this.needsImport = true;
    const attributes = [
      attribute('model', modelText),
      attribute('shown', shownText),
    ];
    if (last >= first && modelText !== shownText) {
      attributes.push(attribute('lines', `${prefix.length + first + 1}-${prefix.length + last + 1}`));
    }
    if (words.embed !== null) {
      attributes.push(attribute('embed', words.embed));
    }
    if (words.open) {
      attributes.push(attribute('open', null));
    }
    const element: MdxJsxFlowElement = { type: 'mdxJsxFlowElement', name: COMPONENT, attributes, children: [] };
    return [element];
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
    const page = new Page();
    transform(root, page);
    if (page.needsImport) {
      root.children.unshift(importNode());
    }
  };
}
