/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { FlavorInfo } from '@semantifyr/editor-common';
import { SYSML_FLAVOR_INFO } from './lib/session/sysmlBridge';

const importedTutorialOxsts = import.meta.glob('./snippets/imported/tutorial/*.oxsts', {
  query: '?raw',
  import: 'default',
  eager: true,
});

const importedGammaSources = import.meta.glob('./snippets/imported/gamma/*.gamma', {
  query: '?raw',
  import: 'default',
  eager: true,
});

const importedGammaCompiled = import.meta.glob('./snippets/imported/gamma/*.oxsts', {
  query: '?raw',
  import: 'default',
  eager: true,
});

const importedSysmlv2Sources = import.meta.glob('./snippets/imported/sysmlv2/*.sysml', {
  query: '?raw',
  import: 'default',
  eager: true,
});

const importedSysmlv2Compiled = import.meta.glob('./snippets/imported/sysmlv2/*.oxsts', {
  query: '?raw',
  import: 'default',
  eager: true,
});

interface SysmlCorpusManifest {
  models: { file: string; example?: { label: string; description: string } }[];
}

export const importedSysmlv2Manifest = import.meta.glob<SysmlCorpusManifest>('./snippets/imported/sysmlv2/manifest.json', {
  import: 'default',
  eager: true,
});

export interface LiveExample {
  id: string;
  flavor: string;
  label: string;
  description: string;
  code: string;
}

export interface LiveFlavor extends FlavorInfo {
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
  {
    id: 'advanced',
    label: 'Tutorial: Advanced',
    description:
      'Final state from the "Advanced Tutorial".',
  },
  {
    id: 'level-crossing',
    label: 'Level crossing',
    description: 'Two tracks, a controller and a barrier; the controller forgets a second train.',
  },
];

const SYSMLV2_IMPORTED: readonly ImportedDescriptor[] = Object.values(importedSysmlv2Manifest).flatMap((manifest) =>
  manifest.models.flatMap((model) =>
    model.example ? [{ id: model.file.replace(/\.sysml$/, ''), ...model.example }] : [],
  ),
);

const GAMMA_IMPORTED: readonly ImportedDescriptor[] = [
  {
    id: 'LevelCrossing',
    label: 'Level crossing',
    description: 'Two tracks, a controller and a barrier; the controller forgets a second train.',
  },
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
  registry: Record<string, unknown>,
  path: string,
  build: (code: string) => T,
): T | null {
  const code = registry[path];
  return typeof code === 'string' ? build(code) : null;
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

function buildImportedSysmlv2Examples(): LiveExample[] {
  return SYSMLV2_IMPORTED.flatMap((descriptor) => {
    const example = loadOrSkip(
      importedSysmlv2Sources,
      `./snippets/imported/sysmlv2/${descriptor.id}.sysml`,
      (code) => ({
        id: descriptor.id,
        flavor: 'sysml' as const,
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

const blank = (flavor: string, libraryName: string | null): LiveExample => ({
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

const sysmlExamples: LiveExample[] = [
  ...buildImportedSysmlv2Examples(),
  blank('sysml', null),
];

const BUNDLED_EXAMPLES: Readonly<Record<string, LiveExample[]>> = {
  'oxsts': oxstsExamples,
  'oxsts-with-gamma-library': oxstsWithGammaLibraryExamples,
  'oxsts-with-sysmlv2-library': oxstsWithSysmlv2LibraryExamples,
  'gamma': gammaExamples,
  [SYSML_FLAVOR_INFO.id]: sysmlExamples,
};

function withExamples(info: FlavorInfo): LiveFlavor {
  return { ...info, examples: BUNDLED_EXAMPLES[info.id] ?? [blank(info.id, null)] };
}

// The served flavors keep the server's order; the client-only SysML flavor comes last.
export function liveFlavors(served: readonly FlavorInfo[]): [LiveFlavor, ...LiveFlavor[]] {
  const sysml = withExamples(SYSML_FLAVOR_INFO);
  const [first, ...rest] = served.map(withExamples);
  if (first === undefined) {
    return [sysml];
  }
  return [first, ...rest, sysml];
}

export function findFlavor(flavors: readonly LiveFlavor[], id: string | null | undefined): LiveFlavor | undefined {
  return flavors.find((flavor) => {
    return flavor.id === id;
  });
}

export function findExample(flavor: LiveFlavor, id: string | null | undefined): LiveExample | undefined {
  return flavor.examples.find((example) => {
    return example.id === id;
  });
}
