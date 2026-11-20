/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest'

import { shownView, traceLanguageOf } from '../../trace/registry'
import { failedResult } from '../fixtures/verification-results'

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
  return {}
})

vi.mock('monaco-editor', () => {
  return {}
})

function viewsOf(languageId: string): string[] {
  return traceLanguageOf(languageId).views.map((view) => {
    return view.id
  })
}

describe('the views of a trace', () => {
  it('open on the language\'s own trace text in Gamma and SysML, on the steps in OXSTS and in a language without views of its own', () => {
    expect(viewsOf('sysml')[0]).toBe('own')
    expect(viewsOf('gamma')[0]).toBe('own')
    expect(viewsOf('oxsts')[0]).toBe('steps')
    expect(viewsOf('unknown')).toEqual(viewsOf('oxsts'))
  })

  it('show the view chosen for the language, or the first when the chosen one has nothing to show of the trace', () => {
    const gamma = traceLanguageOf('gamma')
    const context = { witnessUri: failedResult.witnessUri, traceUri: 'inmemory:///workspace/snippet.trace.get', caseName: 'Case' }
    const withoutWitness = { ...context, witnessUri: null }

    expect(shownView(gamma, 'calls', context).id).toBe('calls')
    expect(shownView(gamma, undefined, context).id).toBe('own')
    expect(shownView(gamma, 'removed', context).id).toBe('own')
    expect(shownView(gamma, 'witness', withoutWitness).id).toBe('own')
  })
})
