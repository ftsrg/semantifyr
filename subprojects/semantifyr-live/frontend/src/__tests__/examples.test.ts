/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import {
  LIVE_FLAVORS,
  findExample,
  findExampleAcrossFlavors,
  findFlavor,
} from '../examples';

describe('LIVE_FLAVORS registry', () => {
  it('exposes the three v1 flavors in display order', () => {
    expect(LIVE_FLAVORS.map((f) => f.id)).toEqual(['oxsts', 'xsts', 'gamma']);
  });

  it('every flavor has at least one example with a valid id and label', () => {
    for (const flavor of LIVE_FLAVORS) {
      expect(flavor.examples.length).toBeGreaterThan(0);
      for (const example of flavor.examples) {
        expect(example.id.length).toBeGreaterThan(0);
        expect(example.label.length).toBeGreaterThan(0);
        // Blank examples have empty code — that's intentional. Named tutorial
        // snapshots must have non-empty code.
        if (example.id !== 'blank') {
          expect(example.code.length).toBeGreaterThan(0);
        }
      }
    }
  });

  it('example ids are unique within a flavor', () => {
    for (const flavor of LIVE_FLAVORS) {
      const ids = flavor.examples.map((e) => e.id);
      expect(new Set(ids).size).toBe(ids.length);
    }
  });

  it('every example references its parent flavor id', () => {
    for (const flavor of LIVE_FLAVORS) {
      for (const example of flavor.examples) {
        expect(example.flavor).toBe(flavor.id);
      }
    }
  });

  it('the trafficlight tutorial snapshots are registered as named oxsts examples', () => {
    const oxsts = findFlavor('oxsts')!;
    const ids = oxsts.examples.map((e) => e.id);
    expect(ids).toContain('trafficlight-direct-snapshot');
    expect(ids).toContain('trafficlight-library-snapshot');
  });

  it('the trafficlight snapshots contain code that mentions a verification case', () => {
    const direct = findExample(findFlavor('oxsts')!, 'trafficlight-direct-snapshot');
    expect(direct?.code).toContain('@VerificationCase');
  });
});

describe('findFlavor', () => {
  it('returns the flavor when the id matches exactly', () => {
    expect(findFlavor('oxsts')?.id).toBe('oxsts');
  });

  it('returns undefined for an unknown id', () => {
    expect(findFlavor('cobol')).toBeUndefined();
  });

  it('returns undefined for null and empty inputs', () => {
    expect(findFlavor(null)).toBeUndefined();
    expect(findFlavor('')).toBeUndefined();
  });
});

describe('findExample', () => {
  it('returns an example by id within the supplied flavor', () => {
    const oxsts = findFlavor('oxsts')!;
    expect(findExample(oxsts, 'trafficlight-direct-snapshot')?.label).toContain('traffic light');
  });

  it('returns undefined when the id does not exist in the flavor', () => {
    const oxsts = findFlavor('oxsts')!;
    expect(findExample(oxsts, 'no-such-id')).toBeUndefined();
  });
});

describe('findExampleAcrossFlavors', () => {
  it('locates an oxsts example without a flavor hint', () => {
    const hit = findExampleAcrossFlavors('trafficlight-direct-snapshot');
    expect(hit?.flavor.id).toBe('oxsts');
    expect(hit?.example.id).toBe('trafficlight-direct-snapshot');
  });

  it('returns undefined when no flavor has the example', () => {
    expect(findExampleAcrossFlavors('nonexistent')).toBeUndefined();
  });

  it('disambiguates correctly when multiple flavors define an id with the same name', () => {
    // Both xsts and gamma have a "blank" example. The lookup walks flavors in
    // declaration order, so the xsts blank wins.
    const hit = findExampleAcrossFlavors('blank');
    expect(hit?.flavor.id).toBe('xsts');
  });
});
