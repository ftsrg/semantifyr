/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import { servedExamplesOf } from '@semantifyr/editor-common/examples';
import {
  descriptionOf,
  findExample,
  findFlavor,
  listedExamples,
  liveFlavors,
} from '../../examples';
import { SYSML_FLAVOR_INFO } from '../../lib/session/sysmlBridge';
import { servedFlavors } from '../fixtures/flavors';

const servedLiveFlavors = liveFlavors(servedFlavors);

describe('liveFlavors', () => {
  it('keeps the served order and appends the client-only SysML flavor', () => {
    expect(servedLiveFlavors.map((flavor) => {
      return flavor.id;
    })).toEqual([
      'oxsts',
      'oxsts-with-gamma-library',
      'oxsts-with-sysmlv2-library',
      'gamma',
      'sysml',
    ]);
  });

  it('takes the served fields of a flavor from the server', () => {
    expect(findFlavor(servedLiveFlavors, 'gamma')).toMatchObject(servedFlavors[3]!);
    expect(findFlavor(servedLiveFlavors, 'sysml')).toMatchObject(SYSML_FLAVOR_INFO);
  });

  it('gives a served flavor without bundled examples a blank one', () => {
    const flavors = liveFlavors([
      { ...servedFlavors[0]!, id: 'oxsts-with-another-library' },
    ]);

    expect(flavors[0].examples.map((example) => {
      return example.id;
    })).toEqual([
      'blank',
    ]);
    expect(flavors[0].examples[0]!.flavor).toBe('oxsts-with-another-library');
  });

  it('lists only the SysML flavor when the server serves none', () => {
    expect(liveFlavors([]).map((flavor) => {
      return flavor.id;
    })).toEqual([
      'sysml',
    ]);
  });

  it('the sysml flavor offers the registry\'s SysML examples as SysML source', () => {
    const flavor = findFlavor(servedLiveFlavors, 'sysml');

    expect(flavor?.languageId).toBe('sysml');
    expect(flavor?.examples.map((example) => {
      return example.id;
    })).toEqual([
      ...servedExamplesOf('sysml').map((example) => {
        return example.id;
      }),
      'blank',
    ]);
    expect(flavor?.examples[0]?.code).toContain('verification def');
  });

  it('every flavor has at least one example with a valid id and label', () => {
    for (const flavor of servedLiveFlavors) {
      expect(flavor.examples.length).toBeGreaterThan(0);
      for (const example of flavor.examples) {
        expect(example.id.length).toBeGreaterThan(0);
        expect(example.label.length).toBeGreaterThan(0);
        // Blank examples have empty code - that's intentional. Named tutorial
        // snapshots must have non-empty code.
        if (example.id !== 'blank') {
          expect(example.code.length).toBeGreaterThan(0);
        }
      }
    }
  });

  it('example ids are address names, lower-case with hyphens', () => {
    for (const flavor of servedLiveFlavors) {
      for (const example of flavor.examples) {
        expect(example.id).toMatch(/^[a-z0-9]+(-[a-z0-9]+)*$/);
      }
    }
  });

  it('the blank Gamma model names its package, which a Gamma model must', () => {
    const gamma = findFlavor(servedLiveFlavors, 'gamma')!;
    expect(findExample(gamma, 'blank')?.code).toMatch(/^package \w+\n$/);
  });

  it('example ids are unique within a flavor', () => {
    for (const flavor of servedLiveFlavors) {
      const ids = flavor.examples.map((example) => example.id);
      expect(new Set(ids).size).toBe(ids.length);
    }
  });

  it('every example references its parent flavor id', () => {
    for (const flavor of servedLiveFlavors) {
      for (const example of flavor.examples) {
        expect(example.flavor).toBe(flavor.id);
      }
    }
  });

  it('the trafficlight tutorial snapshots are registered as named oxsts examples', () => {
    const oxsts = findFlavor(servedLiveFlavors, 'oxsts')!;
    const ids = oxsts.examples.map((example) => example.id);
    expect(ids).toContain('intersection');
    expect(ids).toContain('library');
    expect(ids).toContain('hierarchy');
  });

  it('the trafficlight snapshots contain code that mentions a verification case', () => {
    const direct = findExample(findFlavor(servedLiveFlavors, 'oxsts')!, 'intersection');
    expect(direct?.code).toContain('@VerificationCase');
  });
});

describe('the registry\'s examples', () => {
  it('a library offers the compiled form of each example its language\'s menu lists', () => {
    for (const [flavorId, language] of [['oxsts-with-sysmlv2-library', 'sysml'], ['oxsts-with-gamma-library', 'gamma']] as const) {
      const compiled = listedExamples(findFlavor(servedLiveFlavors, flavorId)!).map((example) => {
        return example.id;
      });
      const listed = servedExamplesOf(language).filter((example) => {
        return example.hidden !== true;
      }).map((example) => {
        return example.id;
      });
      expect(compiled).toEqual(listed);
    }
  });

  it('a lesson\'s step opens by its address and the menu does not list it', () => {
    const oxsts = findFlavor(servedLiveFlavors, 'oxsts')!;

    expect(findExample(oxsts, 'intersection-step1')?.code).toContain('package example::trafficlight::intersection::step1');
    expect(listedExamples(oxsts).map((example) => {
      return example.id;
    })).not.toContain('intersection-step1');
  });

  it('shows no description for a model whose file has no comment at its top', () => {
    const descriptions = {
      './snippets/imported/descriptions/lights.oxsts.description': ' Two lights at a crossing.\n',
    };

    expect(descriptionOf(descriptions, 'lights.oxsts')).toBe('Two lights at a crossing.');
    expect(descriptionOf(descriptions, 'bare.oxsts')).toBeNull();
  });
});

describe('findFlavor', () => {
  it('returns the flavor when the id matches exactly', () => {
    expect(findFlavor(servedLiveFlavors, 'oxsts')?.id).toBe('oxsts');
  });

  it('returns undefined for an unknown id', () => {
    expect(findFlavor(servedLiveFlavors, 'cobol')).toBeUndefined();
  });

  it('returns undefined for null and empty inputs', () => {
    expect(findFlavor(servedLiveFlavors, null)).toBeUndefined();
    expect(findFlavor(servedLiveFlavors, '')).toBeUndefined();
  });
});

describe('findExample', () => {
  it('returns an example by id within the supplied flavor', () => {
    const examples = [
      { id: 'first', flavor: 'oxsts', label: 'First', description: null, code: '', served: null },
      { id: 'second', flavor: 'oxsts', label: 'Second', description: null, code: '', served: null },
    ];

    expect(findExample({ examples }, 'second')).toBe(examples[1]);
  });

  it('returns undefined when the id does not exist in the flavor', () => {
    const oxsts = findFlavor(servedLiveFlavors, 'oxsts')!;
    expect(findExample(oxsts, 'no-such-id')).toBeUndefined();
  });
});
