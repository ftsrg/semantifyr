/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { LANGUAGES } from '../../texts'
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

export function presentationOf(languageId: string): LanguagePresentation {
  const texts = LANGUAGES[languageId]
  return texts ? { name: texts.name, detail: texts.detail } : { name: languageId, detail: '' }
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
