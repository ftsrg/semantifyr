/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { InitializationSteps, type InitializationStepDefinition } from '../stores/initialization'

export interface LanguagePresentation {
  name: string
  detail: string
  // what the initialization's library step loads
  library: string
  initializationNote: string
}

// The order of the language switch; a language the server adds later goes after these
export const LANGUAGE_ORDER = [
  'sysml',
  'gamma',
  'oxsts',
]

const PRESENTATIONS: Readonly<Record<string, LanguagePresentation>> = {
  sysml: {
    name: 'SysML v2',
    detail: 'The OMG systems modeling language. Semantifyr verifies the behaviour of its parts.',
    library: 'SysML v2 standard library',
    initializationNote: 'The standard library is the slow part; it loads once per visit. The verification cases are known when it has finished.',
  },
  gamma: {
    name: 'Gamma',
    detail: 'Gamma statecharts composed into synchronous systems.',
    library: 'Gamma semantic library',
    initializationNote: 'Takes about a second.',
  },
  oxsts: {
    name: 'OXSTS',
    detail: 'Semantifyr\'s own language of extended transition systems, which the other two compile to.',
    library: 'OXSTS library',
    initializationNote: 'Takes about a second.',
  },
}

export function presentationOf(languageId: string): LanguagePresentation {
  return PRESENTATIONS[languageId] ?? {
    name: languageId,
    detail: '',
    library: `${languageId} library`,
    initializationNote: '',
  }
}

export function hostOf(backendUrl: string): string {
  try {
    return new URL(backendUrl).host
  } catch {
    return backendUrl
  }
}

export function initializationStepsOf(languageId: string, backendUrl: string): InitializationStepDefinition[] {
  const presentation = presentationOf(languageId)
  return [
    { ...InitializationSteps.connect, label: `Connecting to ${hostOf(backendUrl)}` },
    { ...InitializationSteps.server, label: `Starting the ${presentation.name} language server` },
    { ...InitializationSteps.library, label: `Loading the ${presentation.library}` },
    InitializationSteps.cases,
  ]
}
