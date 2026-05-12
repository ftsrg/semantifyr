/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

const importedTutorialOxsts = import.meta.glob('./snippets/imported/tutorial/*.oxsts', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

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
  /** One-line summary shown next to the flavor in the model picker. */
  description: string;
  languageId: string;
  fileName: string;
  peekCompiledOutput: boolean;
  examples: LiveExample[];
}

/**
 * Descriptor for a snippet whose source body is staged on disk by Gradle. The {@code id} doubles
 * as the URL ?example= param AND the on-disk filename (sans extension); collapsing the two means
 * adding a new example is one Gradle task tweak plus one descriptor entry, with no
 * capitalisation or path-mapping logic to keep in sync.
 */
interface ImportedDescriptor {
  id: string;
  label: string;
  description: string;
}

const TUTORIAL_OXSTS: readonly ImportedDescriptor[] = [
  {
    id: 'trafficlight-direct-snapshot',
    label: 'Tutorial: traffic light (direct modeling)',
    description:
      'Final state from the "Direct Modeling" tutorial: a traffic light with a colour-cycling transition relation and two verification cases.',
  },
  {
    id: 'trafficlight-library-snapshot',
    label: 'Tutorial: traffic light (statechart library)',
    description:
      'Final state from the "Building a Library" tutorial: the same traffic light refactored to use a small reusable Statechart base class.',
  },
];

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
    id: 'Spacecraft',
    label: 'Spacecraft',
    description:
      'Composite Gamma model of a small spacecraft with battery, thermal, and orbit-control subsystems plus reachability cases.',
  },
  {
    id: 'Crossroads',
    label: 'Crossroads',
    description:
      'Two coordinated traffic lights at an intersection, exposed via Gamma channels with safety reachability cases.',
  },
  {
    id: 'Simple',
    label: 'Leader-worker',
    description:
      'Two statecharts coordinating via channel-bound events; the canonical leader-worker model used in conformance tests.',
  },
];

function loadOrSkip<T>(
  registry: Record<string, string>,
  path: string,
  build: (code: string) => T,
): T | null {
  const code = registry[path];
  return code !== undefined ? build(code) : null;
}

function buildTutorialOxstsExamples(): LiveExample[] {
  return TUTORIAL_OXSTS.flatMap((descriptor) => {
    const example = loadOrSkip(
      importedTutorialOxsts,
      `./snippets/imported/tutorial/${descriptor.id}.oxsts`,
      (code) => ({
        id: descriptor.id,
        flavor: 'oxsts' as const,
        label: descriptor.label,
        description: descriptor.description,
        code,
      }),
    );
    return example ? [example] : [];
  });
}

function buildImportedGammaExamples(): LiveExample[] {
  return GAMMA_IMPORTED.flatMap((descriptor) => {
    const example = loadOrSkip(
      importedGammaSources,
      `./snippets/imported/gamma/${descriptor.id}.gamma`,
      (code) => ({
        id: descriptor.id,
        flavor: 'gamma' as const,
        label: descriptor.label,
        description: descriptor.description,
        code,
      }),
    );
    return example ? [example] : [];
  });
}

function buildImportedSysmlv2LibraryExamples(): LiveExample[] {
  return SYSMLV2_IMPORTED.flatMap((descriptor) => {
    const example = loadOrSkip(
      importedSysmlv2Compiled,
      `./snippets/imported/sysmlv2/${descriptor.id}.oxsts`,
      (code) => ({
        id: `${descriptor.id}-sysmlv2-library`,
        flavor: 'oxsts-with-sysmlv2-library' as const,
        label: descriptor.label,
        description: descriptor.description,
        code,
      }),
    );
    return example ? [example] : [];
  });
}

function buildImportedGammaLibraryExamples(): LiveExample[] {
  return GAMMA_IMPORTED.flatMap((descriptor) => {
    const example = loadOrSkip(
      importedGammaCompiled,
      `./snippets/imported/gamma/${descriptor.id}.oxsts`,
      (code) => ({
        id: `${descriptor.id}-gamma-library`,
        flavor: 'oxsts-with-gamma-library' as const,
        label: `${descriptor.label} (Gamma library)`,
        description: `OXSTS form of the ${descriptor.label} Gamma model, ready to verify against the Gamma semantic library.`,
        code,
      }),
    );
    return example ? [example] : [];
  });
}

const blank = (flavor: LiveFlavorId, libraryName: string | null): LiveExample => ({
  id: 'blank',
  flavor,
  label: 'Blank',
  description: libraryName
    ? `An empty Semantifyr file with the ${libraryName} library preloaded on the workspace.`
    : 'An empty file, start writing your own model from scratch.',
  code: '',
});

const oxstsExamples: LiveExample[] = [
  ...buildTutorialOxstsExamples(),
];

const oxstsWithGammaLibraryExamples: LiveExample[] = [
  ...buildImportedGammaLibraryExamples(),
  blank('oxsts-with-gamma-library', 'Gamma'),
];

const oxstsWithSysmlv2LibraryExamples: LiveExample[] = [
  ...buildImportedSysmlv2LibraryExamples(),
  blank('oxsts-with-sysmlv2-library', 'SysML v2'),
];

const gammaExamples: LiveExample[] = [
  ...buildImportedGammaExamples(),
  blank('gamma', null),
];

export const LIVE_FLAVORS: readonly LiveFlavor[] = [
  { id: 'oxsts', displayName: 'Semantifyr', description: 'Plain OXSTS - the core modelling language, no extra libraries.', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsExamples },
  { id: 'oxsts-with-gamma-library', displayName: 'Semantifyr with Gamma library', description: 'OXSTS with the Gamma statechart library available to import.', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsWithGammaLibraryExamples },
  { id: 'oxsts-with-sysmlv2-library', displayName: 'Semantifyr with SysML v2 library', description: 'OXSTS with the SysML v2 library available to import.', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsWithSysmlv2LibraryExamples },
  { id: 'gamma', displayName: 'Gamma', description: 'Gamma statechart source - compiled to OXSTS before verification.', languageId: 'gamma', fileName: 'snippet.gamma', peekCompiledOutput: true, examples: gammaExamples },
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
 * flavor. The registry knows which flavor each named snippet belongs to.
 */
export function findExampleAcrossFlavors(id: string | null | undefined): { flavor: LiveFlavor; example: LiveExample } | undefined {
  if (!id) return undefined;
  for (const flavor of LIVE_FLAVORS) {
    const example = flavor.examples.find((e) => e.id === id);
    if (example) return { flavor, example };
  }
  return undefined;
}
