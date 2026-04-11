/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import trafficlightDirectSnapshot from './snippets/trafficlight-direct-snapshot.oxsts?raw';
import trafficlightLibrarySnapshot from './snippets/trafficlight-library-snapshot.oxsts?raw';

export type LiveFlavorId = 'oxsts' | 'xsts' | 'gamma';

export interface LiveExample {
  id: string;
  flavor: LiveFlavorId;
  label: string;
  description: string;
  code: string;
}

export interface LiveFlavor {
  id: LiveFlavorId;
  displayName: string;
  examples: LiveExample[];
}

const oxstsExamples: LiveExample[] = [
  {
    id: 'trafficlight-direct-snapshot',
    flavor: 'oxsts',
    label: 'Tutorial: traffic light (direct modeling)',
    description:
      'Final state from the "Direct Modeling" tutorial: a traffic light with a colour-cycling transition relation and two verification cases.',
    code: trafficlightDirectSnapshot,
  },
  {
    id: 'trafficlight-library-snapshot',
    flavor: 'oxsts',
    label: 'Tutorial: traffic light (statechart library)',
    description:
      'Final state from the "Building a Library" tutorial: the same traffic light refactored to use a small reusable Statechart base class.',
    code: trafficlightLibrarySnapshot,
  },
];

const xstsExamples: LiveExample[] = [
  {
    id: 'blank',
    flavor: 'xsts',
    label: 'Blank',
    description: 'An empty file, start writing your own XSTS model from scratch.',
    code: '',
  },
];

const gammaExamples: LiveExample[] = [
  {
    id: 'blank',
    flavor: 'gamma',
    label: 'Blank',
    description: 'An empty file, start writing your own Gamma model from scratch.',
    code: '',
  },
];

export const LIVE_FLAVORS: readonly LiveFlavor[] = [
  { id: 'oxsts', displayName: 'Semantifyr', examples: oxstsExamples },
  { id: 'xsts', displayName: 'XSTS', examples: xstsExamples },
  { id: 'gamma', displayName: 'Gamma', examples: gammaExamples },
];

export function findFlavor(id: string | null | undefined): LiveFlavor | undefined {
  return LIVE_FLAVORS.find((f) => f.id === id);
}

export function findExample(flavor: LiveFlavor, id: string | null | undefined): LiveExample | undefined {
  return flavor.examples.find((e) => e.id === id);
}

/**
 * Look up an example by id across all flavors. Used when a tutorial links by name with
 * `?example=trafficlight-direct-snapshot` and the URL doesn't redundantly specify the
 * flavor. the registry knows which flavor each named snippet belongs to.
 */
export function findExampleAcrossFlavors(id: string | null | undefined): { flavor: LiveFlavor; example: LiveExample } | undefined {
  if (!id) return undefined;
  for (const flavor of LIVE_FLAVORS) {
    const example = flavor.examples.find((e) => e.id === id);
    if (example) return { flavor, example };
  }
  return undefined;
}
