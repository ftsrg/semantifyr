/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { FlavorInfo } from '@semantifyr/editor-common';
import { EXAMPLE_LANGUAGES, fileNameOf, servedExamplesOf, servedLanguages, type ExampleLanguage, type ServedExample } from '@semantifyr/editor-common/examples';
import { presentationOf } from './lib/language/presentation';
import { SYSML_FLAVOR_INFO } from './lib/session/sysmlBridge';
import { BLANK_MODEL, compiledDescription, compiledLabel } from './texts';

const importedOxsts = import.meta.glob('./snippets/imported/tutorial/*.oxsts', {
  query: '?raw',
  import: 'default',
  eager: true,
});

const importedGamma = import.meta.glob('./snippets/imported/gamma/*', {
  query: '?raw',
  import: 'default',
  eager: true,
});

const importedSysmlv2 = import.meta.glob('./snippets/imported/sysmlv2/*', {
  query: '?raw',
  import: 'default',
  eager: true,
});

const importedDescriptions = import.meta.glob('./snippets/imported/descriptions/*.description', {
  query: '?raw',
  import: 'default',
  eager: true,
});

export interface LiveExample {
  id: string;
  flavor: string;
  label: string;
  description: string | null;
  code: string;
  // null for a blank model
  served: ServedExample | null;
}

export interface LiveFlavor extends FlavorInfo {
  examples: LiveExample[];
}

const IMPORTED: Readonly<Record<ExampleLanguage, { directory: string, files: Record<string, unknown> }>> = {
  oxsts: { directory: 'tutorial', files: importedOxsts },
  gamma: { directory: 'gamma', files: importedGamma },
  sysml: { directory: 'sysmlv2', files: importedSysmlv2 },
};

function importedText(files: Record<string, unknown>, path: string): string | null {
  const text = files[path];
  return typeof text === 'string' ? text : null;
}

function descriptionOf(served: ServedExample): string | null {
  return importedText(importedDescriptions, `./snippets/imported/descriptions/${fileNameOf(served)}.description`)?.trim() ?? null;
}

function sourceExamples(language: ExampleLanguage, flavor: string): LiveExample[] {
  const { directory, files } = IMPORTED[language];
  return servedExamplesOf(language).flatMap((served) => {
    const code = importedText(files, `./snippets/imported/${directory}/${fileNameOf(served)}`);
    return code === null ? [] : [{ id: served.id, flavor, label: served.label, description: descriptionOf(served), code, served }];
  });
}

// the library flavors show the compiled OXSTS of the examples live's menu lists for the library's language
function compiledExamples(language: ExampleLanguage, flavor: string): LiveExample[] {
  const { directory, files } = IMPORTED[language];
  return servedExamplesOf(language).filter((served) => {
    return served.hidden !== true;
  }).flatMap((served) => {
    const name = fileNameOf(served);
    const code = importedText(files, `./snippets/imported/${directory}/${name.slice(0, name.lastIndexOf('.'))}.oxsts`);
    return code === null ? [] : [{ id: served.id, flavor, label: compiledLabel(served.label, presentationOf(language).name), description: compiledDescription(presentationOf(language).name), code, served }];
  });
}

function blank(flavor: string, code = ''): LiveExample {
  return { id: 'blank', flavor, label: BLANK_MODEL, description: null, code, served: null };
}

const BUNDLED_EXAMPLES: Readonly<Record<string, LiveExample[]>> = {
  'oxsts': [
    ...sourceExamples('oxsts', 'oxsts'),
    blank('oxsts', servedLanguages.oxsts.blank),
  ],
  'oxsts-with-gamma-library': [
    ...compiledExamples('gamma', 'oxsts-with-gamma-library'),
    blank('oxsts-with-gamma-library'),
  ],
  'oxsts-with-sysmlv2-library': [
    ...compiledExamples('sysml', 'oxsts-with-sysmlv2-library'),
    blank('oxsts-with-sysmlv2-library'),
  ],
  'gamma': [
    ...sourceExamples('gamma', 'gamma'),
    blank('gamma', servedLanguages.gamma.blank),
  ],
  [SYSML_FLAVOR_INFO.id]: [
    ...sourceExamples('sysml', SYSML_FLAVOR_INFO.id),
    blank(SYSML_FLAVOR_INFO.id, servedLanguages.sysml.blank),
  ],
};

export function bundledExamplesOf(flavorId: string): LiveExample[] {
  return BUNDLED_EXAMPLES[flavorId] ?? [blank(flavorId)];
}

// the website's page on a language, for a language the registry knows
export function languagePageOf(languageId: string): string | null {
  return EXAMPLE_LANGUAGES.includes(languageId as ExampleLanguage) ? servedLanguages[languageId as ExampleLanguage].page : null;
}

// the examples live's menu lists: a lesson's step is opened by its address alone, and the blank model has a place of its own
export function listedExamples(flavor: { examples: readonly LiveExample[] }): LiveExample[] {
  return flavor.examples.filter((example) => {
    return example.served !== null && example.served.hidden !== true;
  });
}

function withExamples(info: FlavorInfo): LiveFlavor {
  return { ...info, examples: bundledExamplesOf(info.id) };
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

export function findExample(flavor: Pick<LiveFlavor, 'examples'>, id: string | null | undefined): LiveExample | undefined {
  return flavor.examples.find((example) => {
    return example.id === id;
  });
}
