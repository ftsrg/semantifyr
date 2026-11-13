/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { EDGES, NODES } from '../components/Phases/InstanceTree';

// Goal: the hand-drawn instance tree draws the instances instantiation builds for the pages' model, in the order it reaches them

const MODEL = readFileSync(fileURLToPath(new URL('../../../../oxsts-test-models/phases/room.oxsts', import.meta.url)), 'utf-8');

interface Drawn {
  path: string;
  type: string;
  feature: string;
}

// the classes' containments of multiplicity one, as the model writes them, reached breadth first from the case
function instancesOfTheModel(): Drawn[] {
  const containments = new Map<string, { name: string, type: string }[]>();
  for (const match of MODEL.matchAll(/class (\w+) \{([\s\S]*?)\n\}/g)) {
    containments.set(match[1] ?? '', [...(match[2] ?? '').matchAll(/contains (\w+): (\w+)\n/g)].map((containment) => {
      return { name: containment[1] ?? '', type: containment[2] ?? '' };
    }));
  }
  const verificationCase = /@VerificationCase\nclass (\w+)/.exec(MODEL)?.[1] ?? '';
  const reached: Drawn[] = [{ path: 'root', type: verificationCase, feature: 'contains root' }];
  // an array's iterator reaches the elements pushed while it runs, which makes the walk breadth first
  for (const instance of reached) {
    for (const containment of containments.get(instance.type) ?? []) {
      reached.push({ path: `${instance.path}.${containment.name}`, type: containment.type, feature: `contains ${containment.name}` });
    }
  }
  return reached;
}

describe('the instance tree drawn on the page on instantiation', () => {
  it('draws the instances of the model, in the order instantiation reaches them, each under the containment that holds it', () => {
    const drawn = NODES.slice(1).map((node, index) => {
      return { path: node.path, type: node.type, feature: EDGES[index]?.feature ?? '' };
    });

    expect(drawn).toEqual(instancesOfTheModel());
    expect(NODES.map((node) => {
      return node.order;
    })).toEqual(Array.from(NODES.keys(), (index) => {
      return index + 1;
    }));
  });
});
