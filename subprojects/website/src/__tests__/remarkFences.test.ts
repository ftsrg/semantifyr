/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Code, Root, RootContent } from 'mdast';
import { describe, expect, it } from 'vitest';

import remarkFences, { fencesOf } from '../plugins/remarkFences';

function fence(meta: string, value: string): Code {
  return { type: 'code', lang: 'oxsts', meta, value };
}

function transformed(...children: RootContent[]): Root {
  const root: Root = { type: 'root', children };
  remarkFences()(root);
  return root;
}

function shown(root: Root): string[] {
  return root.children.filter((child): child is Code => {
    return child.type === 'code';
  }).map((code) => {
    return code.value;
  });
}

function models(...children: RootContent[]): string[] {
  return fencesOf({ type: 'root', children }).map((found) => {
    return found.model;
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
  it('leaves a fence that shows its whole model a code block, without its words', () => {
    const root = transformed(fence('issue=OXSTS.DUPLICATE_NAME', 'class Counter {\n}'));

    expect(root.children).toEqual([{ type: 'code', lang: 'oxsts', meta: null, value: 'class Counter {\n}' }]);
  });

  it('shows the lines between no markers, and keeps every line but the markers in the model', () => {
    const counter = fence('', COUNTER);
    const root = transformed(counter);

    expect(shown(root)).toEqual(['var value: int := 0']);
    expect(models(counter)).toEqual(['class Counter {\nvar value: int := 0\n}']);
  });

  it('continues the previous fence, its chain included, with a blank line between', () => {
    const chain = [
      fence('', 'enum Color {\n  Red\n}'),
      fence('continue', 'class Light {\n}'),
      fence('continue', 'class Lamp {\n}'),
    ];

    expect(shown(transformed(...chain))).toEqual([
      'enum Color {\n  Red\n}',
      'class Light {\n}',
      'class Lamp {\n}',
    ]);
    expect(models(...chain)).toEqual([
      'enum Color {\n  Red\n}',
      'enum Color {\n  Red\n}\n\nclass Light {\n}',
      'enum Color {\n  Red\n}\n\nclass Light {\n}\n\nclass Lamp {\n}',
    ]);
  });

  it('shows nothing of a hidden fence, which a later fence continues', () => {
    const chain = [
      fence('hidden', 'class Item'),
      fence('continue', 'class Box {\n  contains item: Item\n}'),
    ];

    expect(shown(transformed(...chain))).toEqual(['class Box {\n  contains item: Item\n}']);
    expect(models(...chain)[1]).toBe('class Item\n\nclass Box {\n  contains item: Item\n}');
  });

  it('leaves out the blank lines at the edges of what is shown', () => {
    const root = transformed(fence('', '// hide-start\nclass Item\n// hide-end\n\nclass Box\n'));

    expect(shown(root)).toEqual(['class Box']);
  });

  it('fails the page on an unknown word, an unpaired marker and a chain to nothing', () => {
    expect(() => {
      return transformed(fence('hiden', 'class A'));
    }).toThrow('unknown word hiden');
    expect(() => {
      return transformed(fence('', '// hide-start\nclass A'));
    }).toThrow('unpaired // hide-start');
    expect(() => {
      return transformed(fence('', 'class A\n// hide-end'));
    }).toThrow('unpaired // hide-end');
    expect(() => {
      return transformed(fence('continue', 'class A'));
    }).toThrow('continues no earlier fence');
  });

  it('reads the fence test\'s issue word and a fence nested in another element, and no fence of another language', () => {
    const sysml = { type: 'code', lang: 'sysmlv2', meta: 'run', value: 'part def A;' } satisfies Code;
    const root = transformed(
      { type: 'blockquote', children: [fence('issue=OXSTS.DUPLICATE_NAME', '// hide-start\nclass A\n// hide-end\nclass A')] },
      sysml,
    );

    expect(root.children[0]).toMatchObject({ type: 'blockquote', children: [{ type: 'code', value: 'class A' }] });
    expect(root.children[1]).toEqual(sysml);
  });

  it('shows the lines in a hidden class without the indentation the model gives them', () => {
    const counter = fence('', '// hide-start\nclass Counter {\n// hide-end\n  var value: int := 0\n\n  tran step() {\n  }\n// hide-start\n}\n// hide-end');

    expect(shown(transformed(counter))).toEqual(['var value: int := 0\n\ntran step() {\n}']);
    expect(models(counter)).toEqual(['class Counter {\n  var value: int := 0\n\n  tran step() {\n  }\n}']);
  });
});
