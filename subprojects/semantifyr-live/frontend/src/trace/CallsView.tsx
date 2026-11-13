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
const ENVIRONMENT_COLUMN = 104
const ROW = 30
const GUTTER = 34
const BAND_PADDING = 6
const CHARACTER_WIDTH = 6.7
const LIFELINE_LENGTH = 22
const SELF_LOOP = 14
const NESTED_OFFSET = 5
const ARROW_MARGIN = 14
const WIDEST_ARROW = 2 * COLUMN
const LABEL_PADDING = 4
const LABEL_RISE = 6

interface PlacedCall {
  call: TraceCall
  center: number
  // a call enclosed by a call on its own lifeline is drawn beside it
  offset: number
  middle: number
  bottom: number
  label: string
}

interface Band {
  number: number
  top: number
  bottom: number
  calls: PlacedCall[]
}

interface Diagram {
  environment: number
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

const SELF_LABEL_START = 4 + SELF_LOOP + 6

function selfLabelX(placed: PlacedCall): number {
  return placed.center + placed.offset + SELF_LABEL_START
}

function cut(text: string, width: number): string {
  const fitting = Math.max(Math.floor(width / CHARACTER_WIDTH), 1)
  return text.length <= fitting ? text : `${text.slice(0, fitting - 1)}…`
}

interface ArrowSpan {
  first: number
  last: number
  width: number
}

// the environment's lifeline first, then the instances'; neighbours part to fit the text on the arrows between them and beside a self call's loop, up to the widest arrow
function lifelinePositions(steps: readonly { step: TraceCallStep }[], lifelines: readonly string[]): number[] {
  const indices = new Map(lifelines.map((id, index) => {
    return [id, index + 1]
  }))
  const gaps = lifelines.map((_, index) => {
    return index === 0 ? ENVIRONMENT_COLUMN / 2 + COLUMN / 2 : COLUMN
  })
  const spans: ArrowSpan[] = []
  for (const { step } of steps) {
    const offsets: number[] = []
    for (const call of step.calls) {
      const caller = call.enclosing === null ? 0 : indices.get(step.calls[call.enclosing]?.self ?? '') ?? 0
      const callee = indices.get(call.self) ?? 0
      const textWidth = labelOf(call).length * CHARACTER_WIDTH
      if (caller !== callee) {
        offsets.push(0)
        spans.push({ first: Math.min(caller, callee), last: Math.max(caller, callee), width: Math.min(textWidth + 2 * ARROW_MARGIN, WIDEST_ARROW) })
        continue
      }
      const offset = (offsets[call.enclosing ?? -1] ?? 0) + NESTED_OFFSET
      offsets.push(offset)
      if (callee < lifelines.length) {
        spans.push({ first: callee, last: callee + 1, width: Math.min(offset + SELF_LABEL_START + textWidth + ARROW_MARGIN, WIDEST_ARROW) })
      }
    }
  }
  spans.sort((first, second) => {
    return (first.last - first.first) - (second.last - second.first)
  })
  for (const span of spans) {
    const room = gaps.slice(span.first, span.last).reduce((sum, gap) => {
      return sum + gap
    }, 0)
    if (room < span.width) {
      gaps[span.last - 1] = (gaps[span.last - 1] ?? 0) + span.width - room
    }
  }
  const positions = [GUTTER + ENVIRONMENT_COLUMN / 2]
  for (const gap of gaps) {
    positions.push((positions.at(-1) ?? 0) + gap)
  }
  return positions
}

function layout(steps: readonly { number: number, step: TraceCallStep }[], lifelines: readonly string[]): Diagram {
  const positions = lifelinePositions(steps, lifelines)
  const environment = positions[0] ?? GUTTER
  const columns = new Map(lifelines.map((id, index) => {
    return [id, positions[index + 1] ?? environment]
  }))
  const nextColumns = new Map(lifelines.map((id, index) => {
    return [id, positions[index + 2]]
  }))
  let width = (positions.at(-1) ?? environment) + COLUMN / 2
  let y = 0
  const bands = steps.map(({ number, step }) => {
    const top = y
    let cursor = top + BAND_PADDING
    const placed: PlacedCall[] = []
    for (const call of step.calls) {
      const center = columns.get(call.self) ?? environment
      const enclosing = call.enclosing === null ? undefined : placed[call.enclosing]
      const offset = enclosing?.center === center ? enclosing.offset + NESTED_OFFSET : 0
      placed.push({ call, center, offset, middle: cursor + ROW / 2, bottom: 0, label: labelOf(call) })
      cursor += ROW
    }
    placed.forEach((entry, index) => {
      let last = index
      while ((placed[last + 1]?.call.depth ?? 0) > entry.call.depth) {
        last++
      }
      entry.bottom = (placed[last]?.middle ?? entry.middle) + ROW / 2 - 3
      const enclosing = entry.call.enclosing === null ? undefined : placed[entry.call.enclosing]
      if (enclosing?.center === entry.center) {
        const next = nextColumns.get(entry.call.self)
        if (next !== undefined) {
          entry.label = cut(entry.label, next - selfLabelX(entry) - ARROW_MARGIN)
        }
        width = Math.max(width, selfLabelX(entry) + entry.label.length * CHARACTER_WIDTH + 16)
      }
    })
    cursor += BAND_PADDING
    y = cursor
    return { number, top, bottom: cursor, calls: placed }
  })
  return {
    environment,
    lifelines: lifelines.map((id) => {
      return { id, x: columns.get(id) ?? environment }
    }),
    bands,
    width: Math.ceil(width),
    height: y,
  }
}

// the caller's lifeline and how far its activation reaches beside it, none for the environment
interface Caller {
  center: number
  reach: number
}

function Arrow({ placed, caller, fromEnvironment }: { placed: PlacedCall, caller: Caller, fromEnvironment: boolean }): React.JSX.Element {
  const className = fromEnvironment ? 'call-arrow from-environment' : 'call-arrow'
  const y = placed.middle + 6
  if (caller.center === placed.center) {
    const from = caller.center + caller.reach
    const to = placed.center + placed.offset + 4
    const right = to + SELF_LOOP
    return (
      <g className={className}>
        <text x={selfLabelX(placed)} y={placed.middle - 1}>{placed.label}</text>
        <path d={`M${from} ${y - 12} H${right} V${y} H${to + 7}`} />
        <path className="arrowhead" d={`M${to} ${y} l7 -3.5 v7z`} />
      </g>
    )
  }
  const direction = placed.center > caller.center ? 1 : -1
  const from = caller.center + caller.reach * direction
  const to = placed.center - 4 * direction
  return (
    <g className={className}>
      <text className="call-arrow-label" x={(from + to) / 2} y={y - LABEL_RISE}>{cut(placed.label, Math.abs(to - from) - 2 * LABEL_PADDING)}</text>
      <path d={`M${from} ${y} H${to - 7 * direction}`} />
      <path className="arrowhead" d={`M${to} ${y} l${-7 * direction} -3.5 v7z`} />
    </g>
  )
}

export function CallsView({ trace }: TraceViewProps): React.JSX.Element {
  const diagram = useMemo(() => {
    const calls = traceCalls(trace.callTrace)
    const steps = [calls.initialized, ...calls.steps].map((step, number) => {
      return { number, step }
    })
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
          <div className="calls-head calls-head-environment" style={{ left: diagram.environment }} title="What runs the model: init once, then main for every step">
            <span className="calls-head-name">environment</span>
          </div>
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
          <line className="lifeline lifeline-environment" x1={diagram.environment} y1="0" x2={diagram.environment} y2={diagram.height} />
          {diagram.lifelines.map((lifeline) => {
            return <line key={lifeline.id} className="lifeline" x1={lifeline.x} y1="0" x2={lifeline.x} y2={diagram.height} />
          })}
          {diagram.bands.map((band) => {
            return band.calls.map((placed, index) => {
              const enclosing = placed.call.enclosing === null ? undefined : band.calls[placed.call.enclosing]
              const caller = enclosing ? { center: enclosing.center, reach: enclosing.offset + 4 } : { center: diagram.environment, reach: 0 }
              return (
                <g key={`${band.number}-${index}`} className="call">
                  <title>{titleOf(placed.call)}</title>
                  <rect className="activation" x={placed.center + placed.offset - 4} y={placed.middle - 11} width="8" height={Math.max(22, placed.bottom - placed.middle + 11)} rx="2" />
                  <Arrow placed={placed} caller={caller} fromEnvironment={!enclosing} />
                </g>
              )
            })
          })}
        </svg>
      </div>
    </div>
  )
}
