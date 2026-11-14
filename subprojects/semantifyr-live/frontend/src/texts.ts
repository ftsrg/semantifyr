/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerdictStatus } from '@semantifyr/editor-common/marks'
import { portfolioNames, terms, verdictWords } from '@semantifyr/editor-common/terms'

export const PRODUCT_NAME = terms.live.term
export const SHORT_NAME = terms.live.short ?? terms.live.term

export const WEBSITE_URL = 'https://semantifyr.org'
export const PRIVACY_URL = `${WEBSITE_URL}/learn/live-privacy`

export const SOURCE_CODE_URL = 'https://github.com/ftsrg/semantifyr'

export const PAGE_DESCRIPTION = 'Verify a SysML v2, Gamma or OXSTS model in your browser and read its traces in its own language.'

export const ABOUT = {
  sentences: [
    'Semantifyr is a framework to support the declarative definition of engineering model semantics.',
    `With ${SHORT_NAME} you verify a SysML v2, Gamma or OXSTS model in your browser and read its traces in its own language.`,
    `${SHORT_NAME} is a research prototype, made in a PhD project at the Budapest University of Technology and Economics.`,
    'Your model is deleted when you leave.',
  ],
  privacy: 'Privacy',
  website: 'Website',
  sourceCode: 'Source code',
}

export interface LanguageTexts {
  name: string
  detail: string
  // how a case is written, for a model that has none
  writeCase: string
}

export const LANGUAGES: Readonly<Record<string, LanguageTexts>> = {
  sysml: {
    name: 'SysML v2',
    detail: 'OMG\'s Systems Modeling Language',
    writeCase: 'Write a verification def that requires a constraint in mustAlways or eventually.',
  },
  gamma: {
    name: 'Gamma',
    detail: 'The Gamma Statechart Composition Framework',
    writeCase: 'Write a verification case with a component and an AG or EF property.',
  },
  oxsts: {
    name: 'OXSTS',
    detail: 'Semantifyr\'s language for semantics',
    writeCase: 'Mark a class @VerificationCase and give it a prop that returns an AG or EF formula.',
  },
}

export const LIBRARY = {
  heading: terms.library.term,
  choices: {
    'oxsts': { label: 'None', detail: null },
    'oxsts-with-gamma-library': { label: 'Gamma', detail: 'Gamma\'s semantics' },
    'oxsts-with-sysmlv2-library': { label: 'SysML v2', detail: 'SysML v2\'s semantics' },
  } as Readonly<Record<string, { label: string, detail: string | null }>>,
}

export function libraryOf(flavorId: string): { label: string, detail: string | null } {
  return LIBRARY.choices[flavorId] ?? { label: flavorId, detail: null }
}

// the library menu's button, which names the library loaded
export function libraryButton(flavorId: string): string {
  const choice = libraryOf(flavorId)
  return choice.detail === null ? 'No library' : `${choice.label} library`
}

// the status bar's language, with the library where one is loaded
export function languageWithLibrary(languageName: string, flavorId: string): string {
  return libraryOf(flavorId).detail === null ? languageName : `${languageName}, ${libraryButton(flavorId)}`
}

export const DEFAULT_PORTFOLIO_NAME = portfolioNames.smart ?? 'smart'
export const ONE_BACKEND = 'One backend'

export const BACKEND_NAMES: Readonly<Record<string, string>> = {
  theta: 'Theta',
  spin: 'Spin',
  nuxmv: 'nuXmv',
  uppaal: 'Uppaal',
}

export const VERDICT_WORDS: Readonly<Record<VerdictStatus, string>> = {
  'passed': verdictWords.passed,
  'failed': verdictWords.failed,
  'inconclusive': verdictWords.inconclusive,
  'error': verdictWords.error,
  'not-run': 'Not run',
  'running': 'Running',
  'queued': 'Queued',
}

export const RUNNING_OR_QUEUED = 'Running or queued'

// what a row says where the server sent no message
export const CASE_MESSAGES = {
  inconclusive: 'No backend decided the case.',
  error: 'The verification stopped on an error, with no verdict.',
  timeout: 'timed out',
  notSupported: 'not supported',
  cancelled: 'cancelled',
}

export const VALIDATION = {
  explained: 'Validation verifies a case\'s witness again, to check the backend\'s evidence.',
  automatically: 'Validate automatically',
}

export const TRACE_HINTS = {
  passed: 'Passed, and nothing to show: no state of the model fails the case.',
  failed: 'No trace came back for this case.',
  inconclusive: 'Inconclusive: no backend decided the case, so it has no trace.',
  error: 'The case ended in an error, so it has no trace.',
  notYet: 'Its trace shows here once the case has run.',
  select: 'Select a case to see its trace.',
  start: 'Press Verify all; a case\'s trace shows here.',
}

export function noCases(languageId: string): string {
  const writeCase = LANGUAGES[languageId]?.writeCase
  return writeCase === undefined ? 'The model has no verification cases.' : `The model has no verification cases. ${writeCase}`
}

export const SERVER_NOT_ANSWERING = 'The server is not answering. The model can be edited, and its verification cases are listed once it answers.'
export const VERIFY_ALL_HELD = 'Verify all is pressed. Every case runs as soon as initialization has finished.'

export const SHARED_MODEL = 'Shared model'
export const BLANK_MODEL = 'Blank model'

// a library's example is its language's example compiled into OXSTS
export function compiledLabel(label: string, languageName: string): string {
  return `${label} (${languageName} library)`
}

export function compiledDescription(languageName: string): string {
  return `Compiled from the ${languageName} model.`
}

export const PROBLEMS = 'Problems in the model'
export const NO_PROBLEMS = 'No problems.'

export function problemsLabel(errors: number, warnings: number): string {
  return `${PROBLEMS}: ${errors} ${errors === 1 ? 'error' : 'errors'}, ${warnings} ${warnings === 1 ? 'warning' : 'warnings'}`
}

export const THEMES = {
  system: 'System',
  light: 'Light',
  dark: 'Dark',
}

// the full editor keeps Tab for indenting
export function tabHint(key: string): string {
  return `${key} switches Tab to leave the editor`
}

export const TRACE_BANNERS = {
  witness: (caseName: string): string => {
    return `The witness of ${caseName}, in OXSTS.`
  },
  own: (caseName: string, languageName: string): string => {
    return `The trace of ${caseName}, in ${languageName}.`
  },
}

export const DOCUMENTS = {
  trace: 'Trace of the selected case',
  witness: 'Witness of the selected case',
  noTrace: 'select a case with a trace',
}

export const DEVELOPER = {
  server: 'Server',
}

export const APP_ERROR = `${SHORT_NAME} met an unexpected error. Reloading usually helps; edits not saved to a file or a link are lost.`
