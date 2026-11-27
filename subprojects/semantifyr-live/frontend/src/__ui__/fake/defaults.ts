/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type {
  FlavorInfo,
  FlavorsResponse,
  LibrariesResponse,
  PortfoliosResponse,
  ReportTrace,
  VerificationCaseResult,
  VerificationReport,
  WitnessValidationResult,
} from '@semantifyr/editor-common'

import type { InfoResponse } from '../../lib/api/types'
import { reportOf, type ReportSeed } from '../../__tests__/fixtures/reports'

export const INFO: InfoResponse = {
  uptime: 'PT10M',
  startedAt: '2026-11-27T12:00:00Z',
  version: '0.1.0',
  commit: '0123456789',
  activeSessions: 0,
  maxSessions: 256,
}

function oxstsFlavor(id: string, displayName: string, description: string): FlavorInfo {
  return {
    id,
    displayName,
    description,
    languageId: 'oxsts',
    fileName: 'snippet.semantifyr',
    verificationCommand: 'oxsts.case.verify',
    discoveryCommand: 'oxsts.case.discover',
    validateWitnessCommand: 'oxsts.case.validateWitness',
    validateWitnessByCase: false,
    compiledOxstsCommand: null,
  }
}

export const FLAVORS: FlavorsResponse = {
  flavors: [
    oxstsFlavor('oxsts', 'Semantifyr', 'Semantifyr\'s language for semantics'),
    oxstsFlavor('oxsts-with-gamma-library', 'Semantifyr with the Gamma library', 'Gamma\'s semantics'),
    oxstsFlavor('oxsts-with-sysml-library', 'Semantifyr with the SysML v2 library', 'SysML v2\'s semantics'),
    {
      id: 'gamma',
      displayName: 'Gamma',
      description: 'The Gamma Statechart Composition Framework',
      languageId: 'gamma',
      fileName: 'snippet.gamma',
      verificationCommand: 'gamma.case.verify',
      discoveryCommand: 'gamma.case.discover',
      validateWitnessCommand: 'gamma.witness.validate',
      validateWitnessByCase: true,
      compiledOxstsCommand: 'gamma.compiled.oxsts',
    },
  ],
}

export const PORTFOLIOS: PortfoliosResponse = {
  portfolios: [
    {
      id: 'smart',
      displayName: 'Smart',
      description: 'Runs the installed backends in stages and takes the first trusted answer.',
      available: true,
    },
    {
      id: 'all-agree',
      displayName: 'All agree',
      description: 'Runs every installed backend and requires their trusted answers to agree.',
      available: true,
    },
    {
      id: 'theta',
      displayName: 'Theta',
      description: 'Runs Theta\'s configurations side by side and takes the first answer.',
      available: true,
    },
    {
      id: 'spin-safe-dfs',
      displayName: 'Spin',
      description: 'Runs the safe-dfs configuration of spin alone.',
      available: true,
    },
  ],
  defaultPortfolio: 'smart',
}

// long enough that the documents menu scrolls and offers its filter
export const LIBRARIES: LibrariesResponse = {
  builtin: [
    'standard.semantifyr',
  ],
  semantic: {
    'oxsts-with-gamma-library': [
      'Actions.semantifyr',
      'Statecharts.semantifyr',
    ],
    'oxsts-with-sysml-library': [
      'Actions.semantifyr',
      'States.semantifyr',
    ],
    'gamma': [
      'Actions.semantifyr',
      'Statecharts.semantifyr',
    ],
  },
  sysml: [
    'Kernel Libraries/Kernel Data Type Library/Collections.kerml',
    'Kernel Libraries/Kernel Data Type Library/ScalarValues.kerml',
    'Kernel Libraries/Kernel Data Type Library/VectorValues.kerml',
    'Kernel Libraries/Kernel Function Library/BaseFunctions.kerml',
    'Kernel Libraries/Kernel Function Library/BooleanFunctions.kerml',
    'Kernel Libraries/Kernel Function Library/ScalarFunctions.kerml',
    'Kernel Libraries/Kernel Semantic Library/Base.kerml',
    'Kernel Libraries/Kernel Semantic Library/Occurrences.kerml',
    'Kernel Libraries/Kernel Semantic Library/Performances.kerml',
    'Systems Library/Actions.sysml',
    'Systems Library/Attributes.sysml',
    'Systems Library/Items.sysml',
    'Systems Library/Parts.sysml',
    'Systems Library/Ports.sysml',
    'Systems Library/Requirements.sysml',
    'Systems Library/States.sysml',
    'Systems Library/VerificationCases.sysml',
    'Domain Libraries/Quantities and Units/ISQ.sysml',
    'Domain Libraries/Quantities and Units/SI.sysml',
    'Domain Libraries/Quantities and Units/Time.sysml',
  ],
}

export const BUILTIN_LIBRARY_URI = 'file:///workspace/.semantifyr/builtin/standard.semantifyr'

export const BUILTIN_LIBRARY = `package semantifyr

extern datatype bool

extern datatype int

extern datatype string

annotation Trace

annotation VerificationCase
`

export const LAMP = `package lamp

class Lamp {
    var lit: bool := false

    redefine tran {
        lit := true
    }
}

@VerificationCase
class LampNeverLit : Lamp {
    redefine prop {
        return AG !lit
    }
}

@VerificationCase
class LampCanLight : Lamp {
    redefine prop {
        return EF lit
    }
}
`

export function lineOf(text: string, line: string): number {
  const index = text.split('\n').indexOf(line)
  if (index < 0) {
    throw new Error(`no line '${line}' in the text`)
  }
  return index
}

export interface StatedCase {
  id: string
  line: number
  result: VerificationCaseResult
  validation: WitnessValidationResult
  documents: Readonly<Record<string, string>>
}

const LIT_TRACE: ReportTrace = {
  transitions: [],
  variables: [
    {
      variable: 'root.lit',
      type: { kind: 'datatype', name: 'semantifyr::bool' },
      slot: null,
    },
  ],
  initial: { state: 0, calls: [] },
  states: [
    { values: { 'root.lit': false } },
    { values: { 'root.lit': true } },
  ],
  steps: [
    { source: 0, target: 1, elapsed: 0, calls: [] },
  ],
}

export function reportFor(id: string, seed: ReportSeed): VerificationReport {
  return { ...reportOf(seed), verificationCase: id }
}

function witnessUriOf(id: string): string {
  return `file:///workspace/.artifacts/${id.replaceAll('::', '.')}/witness.semantifyr`
}

function witnessOf(id: string): string {
  return `witness of ${id}

var step: int := 0

contains root: ${id}

init {
    assume(step == 0)
    inline root.init()
    step := 1
}
`
}

// a run with a trace writes its witness, which validates
export function statedCase(id: string, line: number, report: VerificationReport, validation = reportFor(id, { verdict: 'Passed', backendId: 'theta', seconds: 0.4, kind: 're-check' })): StatedCase {
  const witnessUri = report.trace === null ? null : witnessUriOf(id)
  return {
    id,
    line,
    result: { status: null, message: null, portfolioId: null, report, witnessUri, traceUri: null },
    validation: { status: 'valid', message: null, portfolioId: null, report: validation },
    documents: witnessUri === null ? {} : { [witnessUri]: witnessOf(id) },
  }
}

export function failing(id: string, line: number): StatedCase {
  return statedCase(id, line, reportFor(id, { verdict: 'Failed', backendId: 'theta', seconds: 1.2, soundness: 'UnderApproximating', trace: LIT_TRACE }))
}

// trace null for a case that holds without a witness
export function passing(id: string, line: number, trace: ReportTrace | null = LIT_TRACE): StatedCase {
  return statedCase(id, line, reportFor(id, { verdict: 'Passed', backendId: 'theta', seconds: 0.8, soundness: 'UnderApproximating', trace }))
}

export const NEVER_LIT = 'LampNeverLit'
export const CAN_LIGHT = 'LampCanLight'

// what discovery finds in any document unless a test states other cases
export const LAMP_CASES: readonly StatedCase[] = [
  failing(`lamp::${NEVER_LIT}`, lineOf(LAMP, `class ${NEVER_LIT} : Lamp {`)),
  passing(`lamp::${CAN_LIGHT}`, lineOf(LAMP, `class ${CAN_LIGHT} : Lamp {`)),
]
