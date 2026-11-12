/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { InitializationSteps, type InitializationStepDefinition } from '../stores/initialization'

export interface LanguagePresentation {
  name: string
  detail: string
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
  },
  gamma: {
    name: 'Gamma',
    detail: 'Gamma statecharts composed into synchronous systems.',
  },
  oxsts: {
    name: 'OXSTS',
    detail: 'Semantifyr\'s own language of extended transition systems, which the other two compile to.',
  },
}

export function presentationOf(languageId: string): LanguagePresentation {
  return PRESENTATIONS[languageId] ?? {
    name: languageId,
    detail: '',
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
    { ...InitializationSteps.initialize, label: `Initializing the ${presentation.name} language server` },
    InitializationSteps.cases,
  ]
}
