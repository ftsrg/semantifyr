/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { VerificationStatus } from '@semantifyr/editor-common'

export interface DeclaredCase {
  name: string
  expected: VerificationStatus | null
}

export interface LoadTestModel {
  name: string
  text: string
  cases: DeclaredCase[]
}

const tutorialDirectory = fileURLToPath(new URL('../../../../../oxsts-test-models/tutorial/', import.meta.url))

const annotatedClass = /((?:@\w+(?:\([^)]*\))?\s*)+)class\s+(\w+)/g

// the expected verdicts are the manifest's beside the model, in the shape the frontends' corpus manifests share
interface CaseManifest {
  models: {
    file: string
    cases: {
      name: string
      verdict: 'Passed' | 'Failed'
    }[]
  }[]
}

const MANIFEST_VERDICTS: Readonly<Record<'Passed' | 'Failed', VerificationStatus>> = {
  Passed: VerificationStatus.Passed,
  Failed: VerificationStatus.Failed,
}

export function expectedVerdicts(path: string): ReadonlyMap<string, VerificationStatus> {
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

export function declaredCases(text: string, expected: ReadonlyMap<string, VerificationStatus>): DeclaredCase[] {
  return [...text.matchAll(annotatedClass)].filter((match) => match[1]?.includes('@VerificationCase')).map((match) => {
    const name = match[2] ?? ''
    return { name, expected: expected.get(name) ?? null }
  })
}

export function readModel(path: string): LoadTestModel {
  const text = readFileSync(path, 'utf-8')
  return { name: basename(path, '.oxsts'), text, cases: declaredCases(text, expectedVerdicts(path)) }
}

export function tutorialStepModels(): LoadTestModel[] {
  return readdirSync(tutorialDirectory).filter((name) => /-step\d+\.oxsts$/.test(name)).sort().map((name) => readModel(tutorialDirectory + name)).filter((model) => model.cases.length > 0)
}
