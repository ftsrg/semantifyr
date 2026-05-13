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
  description: string;
  languageId: string;
  fileName: string;
  peekCompiledOutput: boolean;
  examples: LiveExample[];
}

interface ImportedDescriptor {
  id: string;
  label: string;
  description: string;
}

const TUTORIAL_OXSTS: readonly ImportedDescriptor[] = [
  {
    id: 'basics',
    label: 'Tutorial: Basics',
    description:
      'Final state from the "Direct Modeling" tutorial.',
  },
  {
    id: 'intermediate',
    label: 'Tutorial: Intermediate',
    description:
      'Final state from the "Building a Library" tutorial.',
  },
];

const SYSMLV2_IMPORTED: readonly ImportedDescriptor[] = [
  {
    id: 'compressedspacecraft',
    label: 'Spacecraft (compressed)',
    description: 'Compressed variant of the spacecraft model.',
  },
  {
    id: 'crossroads',
    label: 'Crossroads',
    description: 'Two coordinated traffic lights at an intersection.',
  },
  {
    id: 'door_access',
    label: 'Door access',
    description: 'Door-access model with parallel state regions.',
  },
  {
    id: 'orion_protocol',
    label: 'Orion protocol',
    description: 'Orion protocol with item flows and timing transitions.',
  },
];

const GAMMA_IMPORTED: readonly ImportedDescriptor[] = [
  {
    id: 'Spacecraft',
    label: 'Spacecraft',
    description: 'Small spacecraft with battery, thermal, and orbit-control subsystems.',
  },
  {
    id: 'Crossroads',
    label: 'Crossroads',
    description: 'Two coordinated traffic lights at an intersection.',
  },
  {
    id: 'Simple',
    label: 'Leader-worker',
    description: 'Two statecharts coordinating via channel-bound events.',
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
        description: `Semantifyr form of the ${descriptor.label} Gamma model.`,
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
    ? `Empty file with the ${libraryName} library preloaded.`
    : 'Empty file, start from scratch.',
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
  { id: 'oxsts', displayName: 'Semantifyr', description: 'The core Semantifyr modelling language, no extra libraries.', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsExamples },
  { id: 'oxsts-with-gamma-library', displayName: 'Semantifyr with Gamma library', description: 'Semantifyr with the Gamma statechart library available to import.', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsWithGammaLibraryExamples },
  { id: 'oxsts-with-sysmlv2-library', displayName: 'Semantifyr with SysML v2 library', description: 'Semantifyr with the SysML v2 library available to import.', languageId: 'oxsts', fileName: 'snippet.oxsts', peekCompiledOutput: false, examples: oxstsWithSysmlv2LibraryExamples },
  { id: 'gamma', displayName: 'Gamma', description: 'Gamma statechart source, compiled to Semantifyr before verification.', languageId: 'gamma', fileName: 'snippet.gamma', peekCompiledOutput: true, examples: gammaExamples },
];

export function findFlavor(id: string | null | undefined): LiveFlavor | undefined {
  return LIVE_FLAVORS.find((f) => f.id === id);
}

export function findExample(flavor: LiveFlavor, id: string | null | undefined): LiveExample | undefined {
  return flavor.examples.find((e) => e.id === id);
}

export function findExampleAcrossFlavors(id: string | null | undefined): { flavor: LiveFlavor; example: LiveExample } | undefined {
  if (!id) return undefined;
  for (const flavor of LIVE_FLAVORS) {
    const example = flavor.examples.find((e) => e.id === id);
    if (example) return { flavor, example };
  }
  return undefined;
}
