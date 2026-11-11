/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import type {
  CompiledOxstsResult,
  FlavorsResponse,
  LibrariesResponse,
  PortfoliosResponse,
  VerificationCaseResult,
  VerificationCaseSpecification,
  WitnessValidationResult,
} from '@semantifyr/editor-common'

import type { AdminConfigResponse, AdminStatusResponse, InfoResponse, SessionInfo } from '../../lib/api/types'

export interface Diagnostic {
  range: VerificationCaseSpecification['location']['range']
  severity?: number
  message: string
  source?: string
}

export interface RecordedDocument {
  diagnostics?: Diagnostic[]
  cases?: VerificationCaseSpecification[]
  compiled?: CompiledOxstsResult
}

export interface ProgressValue {
  kind: 'begin' | 'report' | 'end'
  title?: string
  message?: string
  cancellable?: boolean
}

// what one served flavor's sessions answered, keyed by the text of the document, the case, the witness or the address read
export interface FlavorFixture {
  initialize: unknown
  sessionInfo: SessionInfo | null
  documents: Record<string, RecordedDocument>
  results: Record<string, VerificationCaseResult>
  validations: Record<string, WitnessValidationResult>
  reads: Record<string, string | null>
  verifyProgress: ProgressValue[]
  validateProgress: ProgressValue[]
}

export interface RestFixture {
  info: InfoResponse
  flavors: FlavorsResponse
  portfolios: PortfoliosResponse
  libraries: LibrariesResponse
}

export interface AdminFixture {
  whoami: { user: string }
  config: AdminConfigResponse
  status: AdminStatusResponse
}

export const FIXTURES_DIRECTORY = join(import.meta.dirname, 'fixtures')

export function textKey(text: string): string {
  return createHash('sha256').update(text).digest('hex').slice(0, 16)
}

function readJson(name: string): unknown {
  return JSON.parse(readFileSync(join(FIXTURES_DIRECTORY, name), 'utf-8'))
}

let rest: RestFixture | null = null
let admin: AdminFixture | null = null
const flavors = new Map<string, FlavorFixture>()

export function restFixture(): RestFixture {
  rest ??= readJson('rest.json') as RestFixture
  return rest
}

export function adminFixture(): AdminFixture {
  admin ??= readJson('admin.json') as AdminFixture
  return admin
}

export function flavorFixture(flavorId: string): FlavorFixture {
  let fixture = flavors.get(flavorId)
  if (!fixture) {
    fixture = readJson(`${flavorId}.json`) as FlavorFixture
    flavors.set(flavorId, fixture)
  }
  return fixture
}
