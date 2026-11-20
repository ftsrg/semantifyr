/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { render } from '@testing-library/react'
import type { ReportCall, ReportTrace, ReportValue } from '@semantifyr/editor-common'
import { describe, expect, it } from 'vitest'

import { CallsView } from '../../trace/CallsView'

function entry(self: string, transition: string, calls: ReportCall[] = [], argumentValues: Record<string, ReportValue> = {}): ReportCall {
  return { self, transition, arguments: argumentValues, calls }
}

function traceOf(...calls: ReportCall[]): ReportTrace {
  return {
    transitions: [],
    variables: [],
    initial: { state: 0, calls: [] },
    states: [{ values: {} }, { values: {} }],
    steps: [{ source: 0, target: 1, elapsed: 0, calls }],
  }
}

function show(trace: ReportTrace): HTMLElement {
  const { container } = render(
    <CallsView trace={trace} witnessUri={null} traceUri={null} caseName="Case" verdict="failed" expandedStep={null} expandStep={() => {}} runtime={null} openTab={null} growing={false} wrapping={false} />,
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

  it('beside the loop of a self call parts the next lifeline to fit it, cut beyond the widest arrow', () => {
    const nextLifelineAfter = (transition: string): number => {
      return lifelines(show(traceOf(entry('root', transition), entry('root.child', 'go'))))[1]!
    }
    const longTransition = 'switchTheBarrierDownWhileTheTrainApproaches'
    expect(nextLifelineAfter(longTransition)).toBeGreaterThan(nextLifelineAfter('go'))

    const cutTransition = 'b'.repeat(400)
    const container = show(traceOf(entry('root', cutTransition), entry('root.child', 'go')))
    const label = [...container.querySelectorAll('.call text')].find((text) => {
      return text.textContent.startsWith(cutTransition.slice(0, 8))
    })!
    expect(label.textContent).toMatch(/…$/)
    expect(label.closest('.call')?.querySelector('title')?.textContent).toContain(cutTransition)
  })
})

describe('what the report adds to the calls view', () => {
  it('gives a call its argument values', () => {
    const container = show(traceOf(entry('root.child', 'pass', [], { speed: 3, item: { weight: 2 } })))
    expect(arrowLabel(container, 'pass').textContent).toBe('pass(speed = 3, item = {weight: 2})')
  })
})
