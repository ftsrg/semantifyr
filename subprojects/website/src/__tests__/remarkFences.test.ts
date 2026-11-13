/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Code, Root, RootContent } from 'mdast';
import type { MdxJsxFlowElement } from 'mdast-util-mdx-jsx';
import { describe, expect, it } from 'vitest';

import remarkFences from '../plugins/remarkFences';

function fence(meta: string, value: string): Code {
  return { type: 'code', lang: 'oxsts', meta, value };
}

function transformed(...children: RootContent[]): Root {
  const root: Root = { type: 'root', children };
  remarkFences()(root);
  return root;
}

function fences(root: Root): Record<string, string | null>[] {
  return root.children.filter((child): child is MdxJsxFlowElement => {
    return child.type === 'mdxJsxFlowElement';
  }).map((element) => {
    return Object.fromEntries(element.attributes.map((attribute) => {
      return [attribute.type === 'mdxJsxAttribute' ? attribute.name : '', attribute.value as string | null];
    }));
  });
}

const COUNTER = `// hide-start
class Counter {
// hide-end
var value: int := 0
// hide-start
}
// hide-end`;

describe('remarkFences', () => {
  it('leaves a fence that shows its whole model a code block, and adds no import', () => {
    const plain = fence('', 'class Counter {\n}');
    const root = transformed(plain);

    expect(root.children).toEqual([plain]);
  });

  it('shows the lines between no markers, and keeps every line but the markers in the model, which opens at the lines shown', () => {
    const root = transformed(fence('', COUNTER));

    expect(fences(root)).toEqual([
      {
        model: 'class Counter {\nvar value: int := 0\n}',
        shown: 'var value: int := 0',
        lines: '2-2',
      },
    ]);
    expect(root.children[0]).toMatchObject({ type: 'mdxjsEsm', value: 'import SemantifyrFence from "@site/src/components/Fence"' });
  });

  it('continues the previous fence or a checkpoint with a blank line between, and counts the lines in the whole model', () => {
    const root = transformed(
      fence('checkpoint=colors', 'enum Color {\n  Red\n}'),
      fence('continue', 'class Light {\n}'),
      fence('continue=colors', 'class Lamp {\n}'),
    );

    expect(fences(root)).toEqual([
      {
        model: 'enum Color {\n  Red\n}\n\nclass Light {\n}',
        shown: 'class Light {\n}',
        lines: '5-6',
      },
      {
        model: 'enum Color {\n  Red\n}\n\nclass Lamp {\n}',
        shown: 'class Lamp {\n}',
        lines: '5-6',
      },
    ]);
  });

  it('shows nothing of a hidden fence, which a later fence continues', () => {
    const root = transformed(
      fence('hidden', 'class Item'),
      fence('continue', 'class Box {\n  contains item: Item\n}'),
    );

    expect(root.children.filter((child) => {
      return child.type === 'code';
    })).toEqual([]);
    expect(fences(root)).toMatchObject([
      {
        model: 'class Item\n\nclass Box {\n  contains item: Item\n}',
        lines: '3-5',
      },
    ]);
  });

  it('leaves out the blank lines at the edges of what is shown from the lines it opens at', () => {
    const root = transformed(fence('', '// hide-start\nclass Item\n// hide-end\n\nclass Box\n'));

    expect(fences(root)[0]).toMatchObject({ lines: '3-3' });
  });

  it('embeds a fence that is run or edited, and links one that is opened', () => {
    const root = transformed(
      fence('run', '@VerificationCase\nclass Reaches {\n}'),
      fence('edit open', 'class Counter {\n}'),
    );

    expect(fences(root)).toEqual([
      {
        model: '@VerificationCase\nclass Reaches {\n}',
        shown: '@VerificationCase\nclass Reaches {\n}',
        embed: 'run',
      },
      {
        model: 'class Counter {\n}',
        shown: 'class Counter {\n}',
        embed: 'edit',
        open: null,
      },
    ]);
  });

  it('runs a fence whose model holds a verification case, and edits every other', () => {
    expect(() => {
      return transformed(fence('run', 'class Counter {\n}'));
    }).toThrow('A fence that is run holds a verification case');
    expect(() => {
      return transformed(fence('checkpoint=case', '@VerificationCase\nclass Reaches {\n}'), fence('edit continue=case', 'class Counter {\n}'));
    }).toThrow('A fence with a verification case is run, not edited');
  });

  it('fails the page on an unknown word, an unpaired marker, a chain to nothing and a checkpoint set twice', () => {
    expect(() => {
      return transformed(fence('runn', 'class A'));
    }).toThrow('unknown word runn');
    expect(() => {
      return transformed(fence('', '// hide-start\nclass A'));
    }).toThrow('unpaired // hide-start');
    expect(() => {
      return transformed(fence('', 'class A\n// hide-end'));
    }).toThrow('unpaired // hide-end');
    expect(() => {
      return transformed(fence('continue', 'class A'));
    }).toThrow('continues no earlier fence');
    expect(() => {
      return transformed(fence('continue=colors', 'class A'));
    }).toThrow('continues no checkpoint colors');
    expect(() => {
      return transformed(fence('checkpoint=a', 'class A'), fence('checkpoint=a', 'class B'));
    }).toThrow('The checkpoint a is set twice');
  });

  it('reads the fence test\'s issue word and a fence nested in another element, and no fence of another language', () => {
    const sysml = { type: 'code', lang: 'sysmlv2', meta: 'run', value: 'part def A;' } satisfies Code;
    const root = transformed(
      { type: 'blockquote', children: [fence('issue=OXSTS.DUPLICATE_NAME', '// hide-start\nclass A\n// hide-end\nclass A')] },
      sysml,
    );

    expect(root.children[1]).toMatchObject({ type: 'blockquote', children: [{ type: 'mdxJsxFlowElement', name: 'SemantifyrFence' }] });
    expect(root.children[2]).toEqual(sysml);
  });
});
