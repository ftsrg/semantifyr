/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Code, Parent, RootContent } from 'mdast';
import type { MdxJsxAttribute, MdxJsxFlowElement } from 'mdast-util-mdx-jsx';
import type { MdxjsEsm } from 'mdast-util-mdxjs-esm';

// DocumentationFenceTest reads the same words and markers
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
}

// the previous fence of the page, or the fence that set the checkpoint
const PREVIOUS = '';

function infoWordsOf(code: Code, where: string): InfoWords {
  const words: InfoWords = { embed: null, open: false, hidden: false, continues: null, checkpoint: null };
  for (const word of (code.meta ?? '').split(/\s+/)) {
    const [key, value] = word.split('=', 2);
    // issue is the fence test's alone
    if (word === '' || key === 'issue' || key === 'fragment') {
      continue;
    }
    if (key === 'run' || key === 'edit') {
      if (words.embed !== null) {
        throw new Error(`An oxsts fence is run or edited, not both, ${where}`);
      }
      words.embed = key;
    } else if (key === 'open') {
      words.open = true;
    } else if (key === 'hidden') {
      words.hidden = true;
    } else if (key === 'continue') {
      words.continues = value ?? PREVIOUS;
    } else if (key === 'checkpoint' && value !== undefined && value !== '') {
      words.checkpoint = value;
    } else {
      throw new Error(`The oxsts fence has an unknown word ${word}, ${where}`);
    }
  }
  if (words.hidden && (words.embed !== null || words.open)) {
    throw new Error(`A hidden oxsts fence is neither embedded nor opened, ${where}`);
  }
  return words;
}

interface Lines {
  // the fence's lines without the markers
  own: string[];
  // the indices in own of the lines the reader sees
  shown: number[];
}

function linesOf(text: string, where: string): Lines {
  const own: string[] = [];
  const shown: number[] = [];
  let hiding = false;
  for (const line of text.split('\n')) {
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
    own.push(line);
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

class Page {
  needsImport = false;

  private previous: string[] | null = null;

  private readonly checkpoints = new Map<string, string[]>();

  // a continued fence's model is the model it continues, a blank line and its own lines
  fence(code: Code): RootContent[] {
    const where = `line ${code.position?.start.line ?? '?'}`;
    const words = infoWordsOf(code, where);
    const { own, shown } = linesOf(code.value, where);
    let prefix: string[] = [];
    if (words.continues !== null) {
      const continued = words.continues === PREVIOUS ? this.previous : this.checkpoints.get(words.continues) ?? null;
      if (continued === null) {
        throw new Error(`The oxsts fence continues ${words.continues === PREVIOUS ? 'no earlier fence' : `no checkpoint ${words.continues}`}, ${where}`);
      }
      prefix = [...continued, ''];
    }
    const model = [...prefix, ...own];
    this.previous = model;
    if (words.checkpoint !== null) {
      if (this.checkpoints.has(words.checkpoint)) {
        throw new Error(`The checkpoint ${words.checkpoint} is set twice, ${where}`);
      }
      this.checkpoints.set(words.checkpoint, model);
    }
    if (words.hidden) {
      return [];
    }
    const modelText = model.join('\n');
    if (words.embed === 'run' && !VERIFICATION_CASE.test(modelText)) {
      throw new Error(`A fence that is run holds a verification case, ${where}`);
    }
    if (words.embed === 'edit' && VERIFICATION_CASE.test(modelText)) {
      throw new Error(`A fence with a verification case is run, not edited, ${where}`);
    }
    const visible = shown.filter((index) => {
      return own[index]?.trim() !== '';
    });
    const first = visible[0] ?? 0;
    const last = visible[visible.length - 1] ?? -1;
    const shownText = shown.filter((index) => {
      return index >= first && index <= last;
    }).map((index) => {
      return own[index];
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

export default function remarkFences(): (root: Parent) => void {
  return (root) => {
    const page = new Page();
    transform(root, page);
    if (page.needsImport) {
      root.children.unshift(importNode());
    }
  };
}
