/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync, readFileSync } from 'node:fs'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { VerificationStatus } from '@semantifyr/editor-common'
import { fileNameOf, servedExamples, type CaseManifest, type ExampleLanguage, type ServedExample } from '@semantifyr/editor-common/examples'

export interface DeclaredCase {
  name: string
  expected: VerificationStatus | null
}

export interface LoadTestModel {
  // the example's address, <language>/<id>
  name: string
  flavorId: string
  text: string
  cases: DeclaredCase[]
}

const repository = fileURLToPath(new URL('../../../../../', import.meta.url))

// the build writes the compiled OXSTS of the served SysML v2 models here, as sysml-2ls's mapper gives it
const compiledSysmlDirectory = fileURLToPath(new URL('../snippets/imported/sysmlv2/', import.meta.url))

// as live sends a model: SysML v2 as its mapped OXSTS on the SysML v2 library, the others in their own language
const FLAVOR_OF: Readonly<Record<ExampleLanguage, string>> = {
  sysml: 'oxsts-with-sysmlv2-library',
  gamma: 'gamma',
  oxsts: 'oxsts',
}

// a case of unknown verdict is benchmarked and expects nothing
const MANIFEST_VERDICTS: Readonly<Record<'Passed' | 'Failed' | 'Unknown', VerificationStatus | null>> = {
  Passed: VerificationStatus.Passed,
  Failed: VerificationStatus.Failed,
  Unknown: null,
}

export function expectedVerdicts(path: string): ReadonlyMap<string, VerificationStatus | null> {
  const manifestPath = join(dirname(path), 'manifest.json')
  if (!existsSync(manifestPath)) {
    return new Map()
  }
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf-8')) as CaseManifest
  const model = manifest.models.find((candidate) => {
    return candidate.file === basename(path)
  })
  return new Map(model?.cases.map((declaredCase) => {
    return [declaredCase.name, MANIFEST_VERDICTS[declaredCase.verdict]]
  }))
}

function textOf(example: ServedExample): string {
  if (example.language !== 'sysml') {
    return readFileSync(join(repository, example.file), 'utf-8')
  }
  const name = fileNameOf(example)
  const compiled = join(compiledSysmlDirectory, `${name.slice(0, name.lastIndexOf('.'))}.oxsts`)
  if (!existsSync(compiled)) {
    throw new Error(`${compiled} is missing: run ./gradlew :semantifyr-live-frontend:cloneTestModels first`)
  }
  return readFileSync(compiled, 'utf-8')
}

export function servedModel(example: ServedExample): LoadTestModel {
  const expected = expectedVerdicts(join(repository, example.file))
  return {
    name: `${example.language}/${example.id}`,
    flavorId: FLAVOR_OF[example.language],
    text: textOf(example),
    cases: example.cases.map((name) => {
      return { name, expected: expected.get(name) ?? null }
    }),
  }
}

// every served example with a case, or the ones the addresses name
export function servedModels(addresses?: readonly string[]): LoadTestModel[] {
  const examples = addresses === undefined
    ? servedExamples
    : addresses.map((address) => {
        const example = servedExamples.find((candidate) => {
          return `${candidate.language}/${candidate.id}` === address
        })
        if (example === undefined) {
          throw new Error(`the registry serves no ${address}`)
        }
        return example
      })
  return examples.filter((example) => {
    return example.cases.length > 0
  }).map(servedModel)
}
