/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, test } from 'vitest';
import { EXAMPLE_LANGUAGES, fileNameOf, servedExamples, type ExampleLanguage, type ServedExample } from '../examples/examples.ts';

const REPOSITORY = fileURLToPath(new URL('../../../../', import.meta.url));

const DECLARED_CASE: Readonly<Record<ExampleLanguage, RegExp>> = {
  oxsts: /@VerificationCase\s+(?:@\w+(?:\([^)]*\))?\s+)*class\s+(\w+)/g,
  gamma: /^\s*verification\s+case\s+(\w+)/gm,
  sysml: /^\s*verification\s+def\s+(\w+)/gm,
};

// the shape the corpus manifests share; a corpus may add keys of its own
interface CaseManifest {
  models: {
    file: string;
    cases: {
      name: string;
      verdict: string;
    }[];
  }[];
}

function declaredCases(example: ServedExample): string[] {
  const text = readFileSync(join(REPOSITORY, example.file), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  return [...text.matchAll(DECLARED_CASE[example.language])].map((match) => {
    return match[1]!;
  });
}

function manifestBeside(example: ServedExample): CaseManifest {
  return JSON.parse(readFileSync(join(REPOSITORY, dirname(example.file), 'manifest.json'), 'utf8')) as CaseManifest;
}

const address = (example: ServedExample): string => {
  return `/${example.language}/${example.id}`;
};

describe.each(servedExamples.map((example) => {
  return [address(example), example] as const;
}))('the served example %s', (_, example) => {
  test('has its file', () => {
    expect(existsSync(join(REPOSITORY, example.file))).toBe(true);
  });

  test('names the cases its file declares', () => {
    expect(example.cases).toEqual(declaredCases(example));
  });

  test('names its origin, its rights holder and its licence', () => {
    expect(example.origin.trim()).not.toBe('');
    expect(example.holder.trim()).not.toBe('');
    expect(example.licence.trim()).not.toBe('');
  });

  test('finds the expected verdict of each case in the manifest beside its file', () => {
    const model = manifestBeside(example).models.find((candidate) => {
      return candidate.file === fileNameOf(example);
    });
    const verdicts = example.cases.map((name) => {
      return model?.cases.find((candidate) => {
        return candidate.name === name;
      })?.verdict;
    });
    for (const verdict of verdicts) {
      expect(['Passed', 'Failed']).toContain(verdict);
    }
  });
});

describe('the registry', () => {
  test('gives each example one address', () => {
    const addresses = servedExamples.map(address);
    expect(new Set(addresses).size).toBe(addresses.length);
  });

  test('lists an example of each language in live\'s menu', () => {
    for (const language of EXAMPLE_LANGUAGES) {
      expect(servedExamples.some((example) => {
        return example.language === language && example.hidden !== true;
      })).toBe(true);
    }
  });
});
