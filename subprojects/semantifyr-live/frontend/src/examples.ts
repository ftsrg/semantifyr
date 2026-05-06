/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import trafficlightDirectSnapshot from './snippets/trafficlight-direct-snapshot.oxsts?raw';
import trafficlightLibrarySnapshot from './snippets/trafficlight-library-snapshot.oxsts?raw';
import leaderWorkerGamma from './snippets/leader-worker.gamma?raw';
import leaderWorkerGammaLibrary from './snippets/leader-worker-gamma-library.oxsts?raw';

// Models populated by the cloneTestModels Gradle task. The maps are eagerly resolved so that
// vitest and the production Vite build see the same content; missing imports just collapse
// the corresponding examples list. Run `./gradlew :semantifyr-live-frontend:cloneTestModels`
// (or any task that depends on it, e.g. assembleFrontend / test) to populate them.
const importedGammaSources = import.meta.glob('./snippets/imported/gamma/*.gamma', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;
const importedGammaCompiled = import.meta.glob('./snippets/imported/gamma/*.oxsts', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;
const importedSysmlv2Compiled = import.meta.glob('./snippets/imported/sysmlv2/*.oxsts', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

export type LiveFlavorId =
  | 'oxsts'
  | 'oxsts-with-gamma-library'
  | 'oxsts-with-sysmlv2-library'
  | 'gamma';

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

interface ImportedDescriptor {
  id: string;
  label: string;
  description: string;
}

const SYSMLV2_IMPORTED: readonly ImportedDescriptor[] = [
  {
    id: 'compressedspacecraft',
    label: 'Spacecraft (compressed)',
    description: 'Compressed variant of the spacecraft model used as a smaller fixture in conformance tests.',
  },
  {
    id: 'crossroads',
    label: 'Crossroads',
    description: 'Two coordinated traffic lights at an intersection, expressed via SysML v2 statecharts and item flows.',
  },
  {
    id: 'door_access',
    label: 'Door access',
    description: 'Door-access SysML v2 model with parallel state regions and item flows.',
  },
  {
    id: 'orion_protocol',
    label: 'Orion protocol',
    description: 'Orion-protocol SysML v2 model exercising item flows and timing-related transitions.',
  },
];

const GAMMA_IMPORTED: readonly ImportedDescriptor[] = [
  {
    id: 'spacecraft',
    label: 'Spacecraft',
    description:
      'Composite Gamma model of a small spacecraft with battery, thermal, and orbit-control subsystems plus reachability cases.',
  },
  {
    id: 'crossroads',
    label: 'Crossroads',
    description:
      'Two coordinated traffic lights at an intersection, exposed via Gamma channels with safety reachability cases.',
  },
  {
    id: 'simple',
    label: 'Leader-worker (Simple)',
    description:
      'Two statecharts coordinating via channel-bound events; the canonical leader-worker model used in conformance tests.',
  },
];

function buildImportedGammaExamples(): LiveExample[] {
  const examples: LiveExample[] = [];
  for (const descriptor of GAMMA_IMPORTED) {
    const sourceFile = `./snippets/imported/gamma/${capitalise(descriptor.id)}.gamma`;
    const code = importedGammaSources[sourceFile];
    if (!code) continue;
    examples.push({ id: descriptor.id, flavor: 'gamma', label: descriptor.label, description: descriptor.description, code });
  }
  return examples;
}

function buildImportedSysmlv2LibraryExamples(): LiveExample[] {
  const examples: LiveExample[] = [];
  for (const descriptor of SYSMLV2_IMPORTED) {
    const sourceFile = `./snippets/imported/sysmlv2/${descriptor.id}.oxsts`;
    const code = importedSysmlv2Compiled[sourceFile];
    if (!code) continue;
    examples.push({
      id: `${descriptor.id}-sysmlv2-library`,
      flavor: 'oxsts-with-sysmlv2-library',
      label: descriptor.label,
      description: descriptor.description,
      code,
    });
  }
  return examples;
}

function buildImportedGammaLibraryExamples(): LiveExample[] {
  const examples: LiveExample[] = [];
  for (const descriptor of GAMMA_IMPORTED) {
    const sourceFile = `./snippets/imported/gamma/${capitalise(descriptor.id)}.oxsts`;
    const code = importedGammaCompiled[sourceFile];
    if (!code) continue;
    examples.push({
      id: `${descriptor.id}-gamma-library`,
      flavor: 'oxsts-with-gamma-library',
      label: `${descriptor.label} (Gamma library)`,
      description: `OXSTS form of the ${descriptor.label} Gamma model, ready to verify against the Gamma semantic library.`,
      code,
    });
  }
  return examples;
}

function capitalise(s: string): string {
  return s.length === 0 ? s : `${s[0]!.toUpperCase()}${s.slice(1)}`;
}

const gammaExamples: LiveExample[] = [
  {
    id: 'leader-worker',
    flavor: 'gamma',
    label: 'Leader-worker (tutorial)',
    description: 'Two statecharts coordinating via channel-bound events, with reachability cases.',
    code: leaderWorkerGamma,
  },
  ...buildImportedGammaExamples(),
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
  ...buildImportedGammaLibraryExamples(),
  {
    id: 'blank',
    flavor: 'oxsts-with-gamma-library',
    label: 'Blank',
    description: 'An empty Semantifyr file with the Gamma library preloaded on the workspace.',
    code: '',
  },
];

const oxstsWithSysmlv2LibraryExamples: LiveExample[] = [
  ...buildImportedSysmlv2LibraryExamples(),
  {
    id: 'blank',
    flavor: 'oxsts-with-sysmlv2-library',
    label: 'Blank',
    description: 'An empty Semantifyr file with the SysML v2 semantic library preloaded on the workspace.',
    code: '',
  },
];

export const LIVE_FLAVORS: readonly LiveFlavor[] = [
  { id: 'oxsts', displayName: 'Semantifyr', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsExamples },
  { id: 'oxsts-with-gamma-library', displayName: 'Semantifyr with Gamma library', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsWithGammaLibraryExamples },
  { id: 'oxsts-with-sysmlv2-library', displayName: 'Semantifyr with SysML v2 library', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsWithSysmlv2LibraryExamples },
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
