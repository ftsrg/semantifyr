/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readdirSync, readFileSync } from 'node:fs'
import { basename } from 'node:path'
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

export function declaredCases(text: string): DeclaredCase[] {
  return [...text.matchAll(annotatedClass)].filter((match) => match[1]?.includes('@VerificationCase')).map((match) => {
    const annotations = match[1] ?? ''
    return {
      name: match[2] ?? '',
      expected: annotations.includes('@Tag("expect-pass")')
        ? VerificationStatus.Passed
        : annotations.includes('@Tag("expect-fail")')
          ? VerificationStatus.Failed
          : null,
    }
  })
}

export function readModel(path: string): LoadTestModel {
  const text = readFileSync(path, 'utf-8')
  return { name: basename(path, '.oxsts'), text, cases: declaredCases(text) }
}

export function tutorialStepModels(): LoadTestModel[] {
  return readdirSync(tutorialDirectory).filter((name) => /-step\d+\.oxsts$/.test(name)).sort().map((name) => readModel(tutorialDirectory + name)).filter((model) => model.cases.length > 0)
}
