/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import type { VerificationTrace } from '@semantifyr/editor-common'
import type { VerdictStatus } from '@semantifyr/editor-common/marks'

import type { LanguageRuntime } from '../lib/language/types'
import type { ReadOnlyDocument } from '../lib/workspace/documentTabs'
import { TRACE_BANNERS } from '../texts'
import { CallsView } from './CallsView'
import { StepsView } from './StepsView'
import { textView, type TraceTextSource } from './TextView'

export interface TraceViewProps {
  trace: VerificationTrace
  caseName: string
  verdict: VerdictStatus
  expandedStep: number | null
  expandStep: (step: number | null) => void
  runtime: LanguageRuntime | null
  // opens a textual view as a read-only tab, null in a shell without tabs
  openTab: ((document: ReadOnlyDocument) => void) | null
  growing: boolean
  wrapping: boolean
}

export type TraceLayout =
  | 'list'
  | 'calls'
  | 'text'

export type TraceSourceContext = Pick<TraceViewProps, 'trace' | 'caseName' | 'runtime'>

export interface TraceView {
  id: string
  label: string
  layout: TraceLayout
  View: React.ComponentType<TraceViewProps>
  // the document a textual view shows, null when the trace has none
  source?: (context: TraceSourceContext) => TraceTextSource | null
}

export interface TraceLanguage {
  // the first is the view a selected case opens on
  views: readonly [TraceView, ...TraceView[]]
}

const CALLS: TraceView = { id: 'calls', label: 'Calls', layout: 'calls', View: CallsView }

function steps(label: string): TraceView {
  return { id: 'steps', label, layout: 'list', View: StepsView }
}

function witness(label: string, uriOf: (trace: VerificationTrace, runtime: LanguageRuntime | null) => string | null | undefined): TraceView {
  return textView('witness', label, (context) => {
    const fileUri = uriOf(context.trace, context.runtime)
    return fileUri
      ? {
        tabId: 'trace-witness',
        fileUri,
        label: `${context.caseName}.witness.oxsts`,
        languageId: 'oxsts',
        banner: TRACE_BANNERS.witness(context.caseName),
      }
      : null
  })
}

function languageTrace(label: string, languageId: string, fileName: (caseName: string) => string, languageName: string): TraceView {
  return textView('own', label, (context) => {
    return context.trace.witnessUri
      ? {
        tabId: 'trace-text',
        fileUri: context.trace.witnessUri,
        label: fileName(context.caseName),
        languageId,
        banner: TRACE_BANNERS.own(context.caseName, languageName),
      }
      : null
  })
}

// the Gamma frontend writes its trace beside the witness
function gammaWitnessOf(trace: VerificationTrace): string | null {
  return trace.witnessUri?.replace(/[^/]*$/, 'witness.oxsts') ?? null
}

const OXSTS_TRACE: TraceLanguage = {
  views: [
    steps('Steps'),
    CALLS,
    witness('Witness', (trace) => {
      return trace.witnessUri
    }),
  ],
}

const TRACE_LANGUAGES: Readonly<Record<string, TraceLanguage>> = {
  sysml: {
    views: [
      languageTrace('SysML trace', 'sysml', (caseName) => {
        return `${caseName}Trace.sysml`
      }, 'SysML v2'),
      steps('OXSTS steps'),
      CALLS,
      witness('OXSTS witness', (trace, runtime) => {
        return trace.witnessUri ? runtime?.witnessUriOf?.(trace.witnessUri) : null
      }),
    ],
  },
  gamma: {
    views: [
      languageTrace('Gamma trace', 'gamma', (caseName) => {
        return `${caseName}.trace.gamma`
      }, 'Gamma'),
      steps('OXSTS steps'),
      CALLS,
      witness('OXSTS witness', gammaWitnessOf),
    ],
  },
  oxsts: OXSTS_TRACE,
}

export function traceLanguageOf(languageId: string): TraceLanguage {
  return TRACE_LANGUAGES[languageId] ?? OXSTS_TRACE
}

// the view chosen for the language, or its first when the chosen one has nothing to show of this trace
export function shownView(language: TraceLanguage, chosen: string | undefined, context: TraceSourceContext): TraceView {
  const view = language.views.find((candidate) => {
    return candidate.id === chosen
  })
  if (!view || view.source?.(context) === null) {
    return language.views[0]
  }
  return view
}
