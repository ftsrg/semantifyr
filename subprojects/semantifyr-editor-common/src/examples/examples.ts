/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import file from './examples.json' with { type: 'json' };

export const EXAMPLE_LANGUAGES = [
  'sysml',
  'gamma',
  'oxsts',
] as const;

export type ExampleLanguage = (typeof EXAMPLE_LANGUAGES)[number];

export interface ServedLanguage {
  page: string;
  blank: string;
}

export interface ServedExample {
  id: string;
  language: ExampleLanguage;
  file: string;
  label: string;
  reason: string;
  origin: string;
  holder: string;
  licence: string;
  page: string;
  cases: string[];
  // a lesson's step, which its page runs and live's menu does not list
  hidden?: boolean;
}

// the expected verdicts of the models beside it, in the shape the corpora share; a corpus may add keys of its own
export interface CaseManifest {
  models: {
    file: string;
    cases: {
      name: string;
      verdict: 'Passed' | 'Failed' | 'Unknown';
      tier?: 'Smoke' | 'Integration' | 'Nightly';
    }[];
  }[];
}

function languageOf(value: string): ExampleLanguage {
  const language = EXAMPLE_LANGUAGES.find((candidate) => {
    return candidate === value;
  });
  if (language === undefined) {
    throw new Error(`The registry names the unknown language ${value}`);
  }
  return language;
}

export const servedLanguages: Readonly<Record<ExampleLanguage, ServedLanguage>> = file.languages;

export const servedExamples: readonly ServedExample[] = file.examples.map((example) => {
  return { ...example, language: languageOf(example.language) };
});

export function servedExamplesOf(language: ExampleLanguage): ServedExample[] {
  return servedExamples.filter((example) => {
    return example.language === language;
  });
}

export function fileNameOf(example: ServedExample): string {
  return example.file.slice(example.file.lastIndexOf('/') + 1);
}

// where an example comes from and under which terms, as live and the website say it
export function rightsOf(example: ServedExample): string {
  return `Origin: ${example.origin}. Rights: ${example.holder}, ${example.licence}.`;
}
