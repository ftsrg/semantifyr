/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { render } from '@testing-library/react'
import type { TraceEntry, VerificationTrace } from '@semantifyr/editor-common'
import { describe, expect, it } from 'vitest'

import { CallsView } from '../../trace/CallsView'

// Goal: a call's text sits centred on its arrow, the lifelines part to fit it up to the widest arrow, beyond which the text is cut and the tooltip keeps it whole; a self call keeps its text beside its loop

function entry(self: string, calledTransition: string, innerTraces: TraceEntry[] = []): TraceEntry {
  return { self, calledTransition, arguments: [], innerTraces }
}

function traceOf(...traces: TraceEntry[]): VerificationTrace {
  return {
    callTrace: { initializedStep: { traces: [] }, steps: [{ traces }] },
    witnessState: { initializedStep: { values: [] }, steps: [] },
    witnessUri: null,
  }
}

function show(trace: VerificationTrace): HTMLElement {
  const { container } = render(
    <CallsView trace={trace} caseName="Case" verdict="failed" expandedStep={null} expandStep={() => {}} runtime={null} openTab={null} growing={false} wrapping={false} />,
  )
  return container
}

function lifelines(container: HTMLElement): number[] {
  return [...container.querySelectorAll('line.lifeline:not(.lifeline-environment)')].map((line) => {
    return Number(line.getAttribute('x1'))
  })
}

function arrowLabel(container: HTMLElement, transition: string): Element {
  const label = [...container.querySelectorAll('.call-arrow-label')].find((text) => {
    return text.textContent.startsWith(transition.slice(0, 8))
  })
  expect(label, transition).toBeDefined()
  return label!
}

describe('the text of a call in the calls view', () => {
  it('is centred between the caller and the callee, for an arrow to the right and to the left', () => {
    const container = show(traceOf(entry('root.child', 'forward', [entry('root', 'back')])))
    const [root, child] = lifelines(container)
    for (const transition of ['forward', 'back']) {
      expect(Number(arrowLabel(container, transition).getAttribute('x'))).toBe((root! + child!) / 2)
    }
  })

  it('parts the lifelines to fit it', () => {
    const shortName = show(traceOf(entry('root.child', 'go')))
    const shortGap = lifelines(shortName)[1]! - lifelines(shortName)[0]!
    const longTransition = 'switchTheBarrierDownWhileTheTrainApproaches'
    const longName = show(traceOf(entry('root.child', longTransition)))
    const longGap = lifelines(longName)[1]! - lifelines(longName)[0]!
    expect(longGap).toBeGreaterThan(shortGap)
    expect(arrowLabel(longName, longTransition).textContent).toBe(`${longTransition}()`)
  })

  it('is cut beyond the widest arrow, the tooltip keeping it whole', () => {
    const transition = 'a'.repeat(400)
    const container = show(traceOf(entry('root.child', transition)))
    const label = arrowLabel(container, transition)
    expect(label.textContent).toMatch(/…$/)
    expect(label.closest('.call')?.querySelector('title')?.textContent).toContain(transition)
  })

  it('stays beside the loop of a self call', () => {
    const container = show(traceOf(entry('root', 'again')))
    const [root] = lifelines(container)
    const label = [...container.querySelectorAll('.call text')].find((text) => {
      return text.textContent === 'again()'
    })
    expect(label?.classList.contains('call-arrow-label')).toBe(false)
    expect(Number(label?.getAttribute('x'))).toBeGreaterThan(root!)
  })
})
