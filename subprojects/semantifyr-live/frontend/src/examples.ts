/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import trafficlightDirectSnapshot from './snippets/trafficlight-direct-snapshot.oxsts?raw';
import trafficlightLibrarySnapshot from './snippets/trafficlight-library-snapshot.oxsts?raw';
import trafficlightXsts from './snippets/trafficlight.xsts?raw';
import leaderWorkerGamma from './snippets/leader-worker.gamma?raw';
import leaderWorkerGammaLibrary from './snippets/leader-worker-gamma-library.oxsts?raw';

export type LiveFlavorId = 'oxsts' | 'oxsts-with-gamma-library' | 'xsts' | 'gamma';

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
  languageId: string;
  fileName: string;
  peekCompiledOutput: boolean;
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
    id: 'trafficlight',
    flavor: 'xsts',
    label: 'Traffic light',
    description: 'A three-colour traffic light with a reachability property.',
    code: trafficlightXsts,
  },
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
    id: 'leader-worker',
    flavor: 'gamma',
    label: 'Leader-worker',
    description: 'Two statecharts coordinating via channel-bound events, with reachability cases.',
    code: leaderWorkerGamma,
  },
  {
    id: 'blank',
    flavor: 'gamma',
    label: 'Blank',
    description: 'An empty file, start writing your own Gamma model from scratch.',
    code: '',
  },
];

const oxstsWithGammaLibraryExamples: LiveExample[] = [
  {
    id: 'leader-worker-gamma-library',
    flavor: 'oxsts-with-gamma-library',
    label: 'Leader-worker (Gamma library)',
    description: 'Semantifyr transcription of the leader-worker Gamma model, built on the Gamma semantic library.',
    code: leaderWorkerGammaLibrary,
  },
  {
    id: 'blank',
    flavor: 'oxsts-with-gamma-library',
    label: 'Blank',
    description: 'An empty Semantifyr file with the Gamma library preloaded on the workspace.',
    code: '',
  },
];

export const LIVE_FLAVORS: readonly LiveFlavor[] = [
  { id: 'oxsts', displayName: 'Semantifyr', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsExamples },
  { id: 'oxsts-with-gamma-library', displayName: 'Semantifyr with Gamma library', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsWithGammaLibraryExamples },
  { id: 'xsts', displayName: 'XSTS', languageId: 'xsts', fileName: 'snippet.xsts', peekCompiledOutput: false, examples: xstsExamples },
  { id: 'gamma', displayName: 'Gamma', languageId: 'gamma', fileName: 'snippet.gamma', peekCompiledOutput: true, examples: gammaExamples },
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
