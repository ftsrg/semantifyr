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
  // the website's page on the language, a path below the site's root
  page: string;
  // the text of the language's blank model
  blank: string;
}

export interface ServedExample {
  // the example's name in live's address, /<language>/<id>
  id: string;
  language: ExampleLanguage;
  // the model's file, from the repository's root
  file: string;
  label: string;
  reason: string;
  origin: string;
  holder: string;
  licence: string;
  // the website's page that explains the example
  page: string;
  cases: string[];
  // a lesson's step that its page runs and live's menu does not list
  hidden?: boolean;
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
