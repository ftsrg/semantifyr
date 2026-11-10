/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import {
  findExample,
  findFlavor,
  importedSysmlv2Manifest,
  liveFlavors,
} from '../examples';
import { SYSML_FLAVOR_INFO } from '../lib/session/sysmlBridge';
import { servedFlavors } from './fixtures/flavors';

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

  it('the sysml flavor offers the corpus examples as SysML source', () => {
    const flavor = findFlavor(servedLiveFlavors, 'sysml');
    const expectedIds = Object.values(importedSysmlv2Manifest).flatMap((manifest) =>
      manifest.models.flatMap((model) => (model.example ? [model.file.replace(/\.sysml$/, '')] : [])),
    );

    expect(flavor?.languageId).toBe('sysml');
    expect(flavor?.examples.map((example) => example.id)).toEqual([...expectedIds, 'blank']);
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
    expect(ids).toContain('basics');
    expect(ids).toContain('intermediate');
    expect(ids).toContain('advanced');
  });

  it('the trafficlight snapshots contain code that mentions a verification case', () => {
    const direct = findExample(findFlavor(servedLiveFlavors, 'oxsts')!, 'basics');
    expect(direct?.code).toContain('@VerificationCase');
  });
});

describe('SysML v2 corpus examples', () => {
  it('registers every example of the corpus manifest with its compiled model', () => {
    const manifests = Object.values(importedSysmlv2Manifest);
    expect(manifests).toHaveLength(1);
    const expected = manifests[0]!.models
      .filter((model) => model.example)
      .map((model) => `${model.file.replace(/\.sysml$/, '')}-sysmlv2-library`);
    const registered = findFlavor(servedLiveFlavors, 'oxsts-with-sysmlv2-library')!
      .examples.filter((example) => example.id !== 'blank')
      .map((example) => example.id);
    expect(expected.length).toBeGreaterThan(0);
    expect(registered).toEqual(expected);
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
    const oxsts = findFlavor(servedLiveFlavors, 'oxsts')!;
    expect(findExample(oxsts, 'basics')?.label).toContain('Basics');
  });

  it('returns undefined when the id does not exist in the flavor', () => {
    const oxsts = findFlavor(servedLiveFlavors, 'oxsts')!;
    expect(findExample(oxsts, 'no-such-id')).toBeUndefined();
  });
});
