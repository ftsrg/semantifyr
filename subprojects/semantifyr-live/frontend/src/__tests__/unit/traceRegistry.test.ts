/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest'

import { shownView, traceLanguageOf } from '../../trace/registry'
import { sampleTrace } from '../fixtures/verification-results'

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
  it('open on the language\'s own trace text in Gamma and SysML and on the steps in OXSTS, in the order of the selector', () => {
    expect(viewsOf('sysml')).toEqual(['own', 'steps', 'calls', 'witness'])
    expect(viewsOf('gamma')).toEqual(['own', 'steps', 'calls', 'witness'])
    expect(viewsOf('oxsts')).toEqual(['steps', 'calls', 'witness'])
    expect(viewsOf('unknown')).toEqual(viewsOf('oxsts'))
  })

  it('show the view chosen for the language, or the first when the chosen one has nothing to show of the trace', () => {
    const gamma = traceLanguageOf('gamma')
    const context = { trace: sampleTrace, caseName: 'Case', runtime: null }
    const withoutWitness = { ...context, trace: { ...sampleTrace, witnessUri: null } }

    expect(shownView(gamma, 'calls', context).id).toBe('calls')
    expect(shownView(gamma, undefined, context).id).toBe('own')
    expect(shownView(gamma, 'removed', context).id).toBe('own')
    expect(shownView(gamma, 'witness', withoutWitness).id).toBe('own')
  })
})
