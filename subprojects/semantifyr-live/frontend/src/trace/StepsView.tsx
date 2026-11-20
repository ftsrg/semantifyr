/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useMemo } from 'react'
import { shortenPath, traceStates } from '@semantifyr/editor-common'

import { Icon } from '../ui/icons'
import type { TraceViewProps } from './registry'

const VARIABLE_LENGTH = 32

// an instance is named by its last segment, a qualified name by its type and literal
function valueName(value: string): string {
  if (value.includes('::')) {
    return value.split('::').slice(-2).join('::')
  }
  return value.startsWith('root.') ? value.slice(value.lastIndexOf('.') + 1) : value
}

function Variable({ path }: { path: string }): React.JSX.Element {
  return <span className="reference" title={path}>{shortenPath(path, VARIABLE_LENGTH)}</span>
}

function Value({ value, className = 'reference' }: { value: string, className?: string }): React.JSX.Element {
  return <span className={className} title={value}>{valueName(value)}</span>
}

export function StepsView({ trace, verdict, expandedStep, expandStep }: TraceViewProps): React.JSX.Element {
  const states = useMemo(() => {
    return traceStates(trace)
  }, [trace])
  return (
    <div className="steps" data-testid="trace-steps">
      <div className="step">
        <span className="step-number">0</span>
        <div className="step-start">
          Start
          {states.start.map((entry) => {
            return (
              <div key={entry.variable} className="step-start-line">
                <Variable path={entry.variable} />
                <span className="step-equals"> = </span>
                <Value value={entry.value} />
              </div>
            )
          })}
        </div>
      </div>
      {states.steps.map(({ elapsed, variables }, index) => {
        const final = index === states.steps.length - 1
        const open = expandedStep === index
        const changes = variables.filter((entry) => {
          return entry.changed
        })
        return (
          <div key={index} className={`step${final ? ` is-final status-${verdict}` : ''}${open ? ' is-open' : ''}`}>
            <span className="step-number">{index + 1}</span>
            <div className="step-items">
              {elapsed !== null
                ? (
                  <div className="step-item is-time">
                    <Icon name="clock" />
                    <span>
                      {'elapsed '}
                      <span className="reference">{elapsed}</span>
                    </span>
                  </div>
                )
                : null}
              {changes.length === 0 ? <div className="step-item is-input"><Icon name="value" /><span>no value changed</span></div> : null}
              {changes.map((change) => {
                return (
                  <div key={change.variable} className="step-item is-value">
                    <Icon name="value" />
                    <span>
                      <Variable path={change.variable} />
                      {' '}
                      {change.previous !== undefined ? <Value value={change.previous} className="step-from" /> : null}
                      <span className="step-arrow">{'→'}</span>
                      <Value value={change.value} />
                    </span>
                  </div>
                )
              })}
              {open
                ? (
                  <div className="step-variables">
                    {variables.map((entry) => {
                      return (
                        <div key={entry.variable} className={`step-variable${entry.changed ? ' is-changed' : ''}`}>
                          <Variable path={entry.variable} />
                          <span className="step-equals">=</span>
                          <Value value={entry.value} />
                        </div>
                      )
                    })}
                  </div>
                )
                : null}
            </div>
            <button
              type="button"
              className="step-toggle"
              aria-expanded={open}
              title={open ? 'Collapse' : 'Every variable at this step'}
              aria-label={open ? `Collapse step ${index + 1}` : `Every variable at step ${index + 1}`}
              onClick={() => {
                expandStep(open ? null : index)
              }}
            >
              <Icon name="chevron-down" size="tiny" />
            </button>
          </div>
        )
      })}
    </div>
  )
}
