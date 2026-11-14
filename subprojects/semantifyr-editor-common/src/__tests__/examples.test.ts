/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, test } from 'vitest';
import { EXAMPLE_LANGUAGES, fileNameOf, servedExamples, type CaseManifest, type ExampleLanguage, type ServedExample } from '../examples/examples.ts';

const REPOSITORY = fileURLToPath(new URL('../../../../', import.meta.url));

const DECLARED_CASE: Readonly<Record<ExampleLanguage, RegExp>> = {
  oxsts: /@VerificationCase\s+(?:@\w+(?:\([^)]*\))?\s+)*class\s+(\w+)/g,
  gamma: /^\s*verification\s+case\s+(\w+)/gm,
  sysml: /^\s*verification\s+def\s+(\w+)/gm,
};

const LANGUAGE_OF_EXTENSION: Readonly<Record<string, ExampleLanguage>> = {
  '.oxsts': 'oxsts',
  '.gamma': 'gamma',
  '.gcd': 'gamma',
  '.sysml': 'sysml',
};

// where the corpora keep their manifests
const MODEL_ROOTS = [
  'oxsts-test-models',
  'subprojects/frontends/gamma/models',
  'subprojects/frontends/sysml/models',
];

function declaredCases(file: string, language: ExampleLanguage): string[] {
  const text = readFileSync(join(REPOSITORY, file), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  return [...text.matchAll(DECLARED_CASE[language])].map((match) => {
    return match[1]!;
  });
}

function furtherCasesOf(file: string): string {
  return `${file.slice(0, file.length - extname(file).length)}.further-cases`;
}

function corpusCases(file: string, language: ExampleLanguage): string[] {
  const fragment = furtherCasesOf(file);
  if (!existsSync(join(REPOSITORY, fragment))) {
    return declaredCases(file, language);
  }
  return [...declaredCases(file, language), ...declaredCases(fragment, language)];
}

// a property's negations and its root operator: a trace exists where the witness does, a reached EF or a broken AG
const PROPERTY_ROOT: Readonly<Record<ExampleLanguage, RegExp>> = {
  oxsts: /return\s+((?:!\s*)*)(EF|AG)\b/,
  gamma: /^\s*((?:not\s+)*)(EF|AG)\b/m,
  sysml: /require\s+constraint\s*\{\s*((?:not\s+)*)(eventually|mustAlways)\s*\(/,
};

function givesTrace(text: string, language: ExampleLanguage, verdict: string | undefined): boolean {
  const root = PROPERTY_ROOT[language].exec(text);
  if (root === null) {
    return false;
  }
  const negated = (root[1]!.match(/!|not/g)?.length ?? 0) % 2 === 1;
  const reachability = (root[2] === 'EF' || root[2] === 'eventually') !== negated;
  return reachability ? verdict === 'Passed' : verdict === 'Failed';
}

function caseText(file: string, language: ExampleLanguage, name: string): string {
  const text = readFileSync(join(REPOSITORY, file), 'utf8');
  const starts = [...text.matchAll(DECLARED_CASE[language])].map((match) => {
    return { name: match[1]!, index: match.index };
  });
  const position = starts.findIndex((start) => {
    return start.name === name;
  });
  return text.slice(starts[position]!.index, starts[position + 1]?.index ?? text.length);
}

// a model describes itself in a comment at its top: `//` lines in OXSTS and Gamma, a `doc` comment in SysML v2
const OPENING_COMMENT: Readonly<Record<ExampleLanguage, RegExp>> = {
  oxsts: /^\s*\/\/ *\S/,
  gamma: /^\s*\/\/ *\S/,
  sysml: /^\s*doc\s*\/\*[\s*]*[^\s*/]/,
};

// Gamma's served models are brought to the checks of a served model by their own lane, which removes this line
const AWAITING_THEIR_LANE: ExampleLanguage = 'gamma';


const manifests = MODEL_ROOTS.flatMap((root) => {
  return readdirSync(join(REPOSITORY, root), { recursive: true, withFileTypes: true }).filter((entry) => {
    return entry.isFile() && entry.name === 'manifest.json';
  }).map((entry) => {
    return relative(REPOSITORY, join(entry.parentPath, entry.name));
  });
});

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
    expect(example.cases).toEqual(declaredCases(example.file, example.language));
  });

  test.skipIf(example.language === AWAITING_THEIR_LANE)('has at most ten cases, so that a reader is not overwhelmed', () => {
    expect(example.cases.length).toBeLessThanOrEqual(10);
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

const listedModels = servedExamples.filter((example) => {
  return example.hidden !== true && example.language !== AWAITING_THEIR_LANE;
});

describe.each(listedModels.map((example) => {
  return [address(example), example] as const;
}))('the served model %s', (_, example) => {
  test('has a case that gives a trace, a reachability case expected to pass or a case expected to fail', () => {
    const model = manifestBeside(example).models.find((candidate) => {
      return candidate.file === fileNameOf(example);
    });
    const tracing = example.cases.filter((name) => {
      const verdict = model?.cases.find((candidate) => {
        return candidate.name === name;
      })?.verdict;
      return givesTrace(caseText(example.file, example.language, name), example.language, verdict);
    });
    expect(tracing).not.toEqual([]);
  });

  test('opens with a comment that describes it', () => {
    expect(readFileSync(join(REPOSITORY, example.file), 'utf8')).toMatch(OPENING_COMMENT[example.language]);
  });
});

describe.each(manifests)('the manifest %s', (manifest) => {
  test('lists for each file the cases the file declares', () => {
    const models = (JSON.parse(readFileSync(join(REPOSITORY, manifest), 'utf8')) as CaseManifest).models;
    expect(models.length).toBeGreaterThan(0);
    for (const model of models) {
      const file = join(dirname(manifest), model.file);
      const listed = model.cases.map((declaredCase) => {
        return declaredCase.name;
      });
      expect(listed, file).toEqual(corpusCases(file, LANGUAGE_OF_EXTENSION[extname(file)]!));
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
