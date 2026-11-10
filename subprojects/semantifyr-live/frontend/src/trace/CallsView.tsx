/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useMemo } from 'react'
import { lastNameSegment, shortenPath, traceCalls, type TraceCall, type TraceCallStep } from '@semantifyr/editor-common'

import { Icon } from '../ui/icons'
import type { TraceViewProps } from './registry'

const COLUMN = 176
const ROW = 30
const GUTTER = 34
const BAND_PADDING = 6
const CHARACTER_WIDTH = 6.7
const LIFELINE_LENGTH = 22
const SELF_LOOP = 14
const NESTED_OFFSET = 5

interface PlacedCall {
  call: TraceCall
  center: number
  // a call enclosed by a call on its own lifeline is drawn beside it
  offset: number
  middle: number
  bottom: number
}

interface Band {
  number: number
  top: number
  bottom: number
  calls: PlacedCall[]
}

interface Diagram {
  lifelines: { id: string, x: number }[]
  bands: Band[]
  width: number
  height: number
}

function argumentName(value: string): string {
  if (value.includes('::')) {
    return lastNameSegment(value)
  }
  return shortenPath(value, LIFELINE_LENGTH)
}

function labelOf(call: TraceCall): string {
  const argumentsText = call.arguments.map((argument) => {
    return `${argument.parameter} = ${argumentName(argument.value)}`
  }).join(', ')
  return `${lastNameSegment(call.calledTransition)}(${argumentsText})`
}

function titleOf(call: TraceCall): string {
  const argumentsText = call.arguments.map((argument) => {
    return `\n${argument.parameter} = ${argument.value}`
  }).join('')
  return `${call.self}\n${call.calledTransition}${argumentsText}`
}

function labelX(placed: PlacedCall, enclosing: PlacedCall | undefined): number {
  const right = placed.center + placed.offset + 4
  return enclosing?.center === placed.center ? right + SELF_LOOP + 6 : right + 7
}

function layout(steps: readonly { number: number, step: TraceCallStep }[], lifelines: readonly string[]): Diagram {
  const columns = new Map(lifelines.map((id, index) => {
    return [id, GUTTER + COLUMN * (index + 0.5)]
  }))
  let width = GUTTER + COLUMN * lifelines.length
  let y = 0
  const bands = steps.map(({ number, step }) => {
    const top = y
    let cursor = top + BAND_PADDING
    const placed: PlacedCall[] = []
    for (const call of step.calls) {
      const center = columns.get(call.self) ?? GUTTER
      const enclosing = call.enclosing === null ? undefined : placed[call.enclosing]
      const offset = enclosing?.center === center ? enclosing.offset + NESTED_OFFSET : 0
      placed.push({ call, center, offset, middle: cursor + ROW / 2, bottom: 0 })
      cursor += ROW
    }
    placed.forEach((entry, index) => {
      let last = index
      while ((placed[last + 1]?.call.depth ?? 0) > entry.call.depth) {
        last++
      }
      entry.bottom = (placed[last]?.middle ?? entry.middle) + ROW / 2 - 3
      const enclosing = entry.call.enclosing === null ? undefined : placed[entry.call.enclosing]
      width = Math.max(width, labelX(entry, enclosing) + labelOf(entry.call).length * CHARACTER_WIDTH + 16)
    })
    if (placed.length === 0) {
      cursor += ROW
    }
    cursor += BAND_PADDING
    y = cursor
    return { number, top, bottom: cursor, calls: placed }
  })
  return {
    lifelines: lifelines.map((id) => {
      return { id, x: columns.get(id) ?? GUTTER }
    }),
    bands,
    width: Math.ceil(width),
    height: y,
  }
}

function Arrow({ placed, enclosing }: { placed: PlacedCall, enclosing: PlacedCall }): React.JSX.Element {
  const y = placed.middle + 6
  if (enclosing.center === placed.center) {
    const from = enclosing.center + enclosing.offset + 4
    const to = placed.center + placed.offset + 4
    const right = to + SELF_LOOP
    return (
      <g className="call-arrow">
        <path d={`M${from} ${y - 12} H${right} V${y} H${to + 7}`} />
        <path className="arrowhead" d={`M${to} ${y} l7 -3.5 v7z`} />
      </g>
    )
  }
  const direction = placed.center > enclosing.center ? 1 : -1
  const from = enclosing.center + (enclosing.offset + 4) * direction
  const to = placed.center - 4 * direction
  return (
    <g className="call-arrow">
      <path d={`M${from} ${y} H${to - 7 * direction}`} />
      <path className="arrowhead" d={`M${to} ${y} l${-7 * direction} -3.5 v7z`} />
    </g>
  )
}

export function CallsView({ trace }: TraceViewProps): React.JSX.Element {
  const diagram = useMemo(() => {
    const calls = traceCalls(trace.callTrace)
    const numbered = calls.steps.map((step, index) => {
      return { number: index + 1, step }
    })
    const steps = calls.initialized.calls.length > 0 ? [{ number: 0, step: calls.initialized }, ...numbered] : numbered
    return calls.lifelines.length > 0 ? layout(steps, calls.lifelines) : null
  }, [trace])
  if (!diagram) {
    return (
      <div className="calls-empty trace-hint" data-testid="trace-no-calls">
        <Icon name="alert" size="small" />
        <span>
          No calls were recorded for this trace. A call is recorded only for a transition annotated with
          {' '}
          <span className="mono">@Trace</span>
          , and this model annotates none.
        </span>
      </div>
    )
  }
  return (
    <div className="calls-scroll" data-testid="trace-calls">
      <div className="calls" style={{ width: diagram.width }}>
        <div className="calls-heads">
          {diagram.lifelines.map((lifeline) => {
            return (
              <div key={lifeline.id} className="calls-head" style={{ left: lifeline.x }} title={lifeline.id}>
                <span className="calls-head-name">{shortenPath(lifeline.id, LIFELINE_LENGTH)}</span>
              </div>
            )
          })}
        </div>
        <svg width={diagram.width} height={diagram.height} viewBox={`0 0 ${diagram.width} ${diagram.height}`}>
          {diagram.bands.map((band) => {
            return (
              <g key={`band-${band.number}`} className="band">
                <rect className="band-fill" x="0" y={band.top} width="100%" height={band.bottom - band.top} />
                <line className="band-rule" x1="0" y1={band.bottom} x2="100%" y2={band.bottom} />
                <text className="band-number" x={GUTTER - 10} y={band.top + BAND_PADDING + ROW / 2 + 4}>{band.number}</text>
              </g>
            )
          })}
          {diagram.lifelines.map((lifeline) => {
            return <line key={lifeline.id} className="lifeline" x1={lifeline.x} y1="0" x2={lifeline.x} y2={diagram.height} />
          })}
          {diagram.bands.map((band) => {
            if (band.calls.length === 0) {
              return <text key={`empty-${band.number}`} className="band-empty" x={GUTTER + 10} y={band.top + BAND_PADDING + ROW / 2 + 4}>no calls</text>
            }
            return band.calls.map((placed, index) => {
              const enclosing = placed.call.enclosing === null ? undefined : band.calls[placed.call.enclosing]
              return (
                <g key={`${band.number}-${index}`} className="call">
                  <title>{titleOf(placed.call)}</title>
                  <rect className="activation" x={placed.center + placed.offset - 4} y={placed.middle - 11} width="8" height={Math.max(22, placed.bottom - placed.middle + 11)} rx="2" />
                  <text x={labelX(placed, enclosing)} y={placed.middle - 1}>{labelOf(placed.call)}</text>
                  {enclosing ? <Arrow placed={placed} enclosing={enclosing} /> : null}
                </g>
              )
            })
          })}
        </svg>
      </div>
    </div>
  )
}
