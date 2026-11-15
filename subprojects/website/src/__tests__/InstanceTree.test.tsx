/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { EDGES, NODES } from '../components/Phases/InstanceTree';
import { readText } from './files';

// Goal: the hand-drawn instance tree draws the instances instantiation builds for the pages' model and where it places them, in the order it reaches them

const MODEL = readText(fileURLToPath(new URL('../../../../oxsts-test-models/phases/room.oxsts', import.meta.url)));

interface Containment {
  name: string;
  type: string;
  shared: boolean;
  // the class that declares the containment first, which a redefinition keeps
  declaredIn: string;
  redefinedIn?: string;
}

interface Placed {
  from: number;
  to: number;
  feature: string;
  redefinedIn?: string;
}

interface Instantiated {
  instances: { path: string, type: string }[];
  placements: Placed[];
}

// a class's containments, its superclass's first, a redefinition in the place of what it redefines
function containmentsOf(): Map<string, Containment[]> {
  const declared = new Map<string, { superclass: string | undefined, containments: { name: string, type: string, shared: boolean, redefines: boolean }[] }>();
  for (const match of MODEL.matchAll(/class (\w+)(?: : (\w+))? \{([\s\S]*?)\n\}/g)) {
    declared.set(match[1] ?? '', {
      superclass: match[2],
      containments: [...(match[3] ?? '').matchAll(/(@Shared\n\s*)?(redefine )?contains (\w+): (\w+)$/gm)].map((containment) => {
        return { name: containment[3] ?? '', type: containment[4] ?? '', shared: containment[1] !== undefined, redefines: containment[2] !== undefined };
      }),
    });
  }
  const effective = (name: string): Containment[] => {
    const declaration = declared.get(name);
    if (declaration === undefined) {
      return [];
    }
    const containments = declaration.superclass === undefined ? [] : effective(declaration.superclass);
    for (const containment of declaration.containments) {
      const redefined = containments.findIndex((inherited) => {
        return inherited.name === containment.name;
      });
      const inherited = containments[redefined];
      if (containment.redefines && inherited !== undefined) {
        containments[redefined] = { ...inherited, type: containment.type, redefinedIn: name };
      } else {
        containments.push({ name: containment.name, type: containment.type, shared: containment.shared, declaredIn: name });
      }
    }
    return containments;
  };
  return new Map([...declared.keys()].map((name) => {
    return [name, effective(name)];
  }));
}

// the containments of multiplicity one, reached breadth first from the case, a shared containment's one instance placed in every holder
function instancesOfTheModel(): Instantiated {
  const containments = containmentsOf();
  const verificationCase = /@VerificationCase\nclass (\w+)/.exec(MODEL)?.[1] ?? '';
  const instances = [{ path: 'the model', type: 'inlined oxsts' }, { path: 'root', type: verificationCase }];
  const placements: Placed[] = [{ from: 0, to: 1, feature: 'contains root' }];
  const shared = new Map<string, number>();
  // the children an instance created, in the order it created them, reached in turn
  const reached = [1];
  for (const holder of reached) {
    const instance = instances[holder];
    for (const containment of containments.get(instance?.type ?? '') ?? []) {
      const key = `${containment.declaredIn}.${containment.name}`;
      let placed = containment.shared ? shared.get(key) : undefined;
      if (placed === undefined) {
        placed = instances.length;
        instances.push({ path: `${instance?.path}.${containment.name}`, type: containment.type });
        reached.push(placed);
        if (containment.shared) {
          shared.set(key, placed);
        }
      }
      placements.push({ from: holder, to: placed, feature: `contains ${containment.name}`, ...(containment.redefinedIn === undefined ? {} : { redefinedIn: containment.redefinedIn }) });
    }
  }
  return { instances, placements };
}

describe('the instance tree drawn on the page on instantiation', () => {
  it('draws the instances of the model and their placements, in the order instantiation reaches them, a shared instance once with an edge from every holder', () => {
    const drawn = {
      instances: NODES.map((node) => {
        return { path: node.path, type: node.type };
      }),
      placements: EDGES,
    };

    expect(drawn).toEqual(instancesOfTheModel());
    expect(NODES.map((node) => {
      return node.order;
    })).toEqual(Array.from(NODES.keys(), (index) => {
      return index + 1;
    }));
  });
});
