/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { Fragment, useMemo, useState } from 'react'

import type { VerificationCaseState } from '../lib/verification/engine'
import { caseRowOf } from '../lib/workspace/caseRows'
import { useCurrentVerification, useLive, useRuntime } from '../lib/workspace/liveContext'
import type { TraceSlotProps } from '../shells/traceSlot'
import { Icon } from '../ui/icons'
import { propertyText } from './property'
import { traceLanguageOf, type TraceView } from './registry'
import { RunDetails } from './RunDetails'

import './trace.css'

function ViewSelector({ views, active, choose }: { views: readonly TraceView[], active: string, choose: (id: string) => void }): React.JSX.Element {
  return (
    <div className="segmented segmented-small segmented-plain trace-views" role="group" aria-label="Views of the trace">
      {views.map((view, index) => {
        return (
          <Fragment key={view.id}>
            {index === 1 ? <span className="views-divider" /> : null}
            <button
              type="button"
              className="segmented-option"
              aria-pressed={view.id === active}
              data-view={view.id}
              onClick={() => {
                choose(view.id)
              }}
            >
              {view.label}
            </button>
          </Fragment>
        )
      })}
    </div>
  )
}

// the case's property, cut from its definition up to the next case's
function useProperty(caseState: VerificationCaseState, languageId: string): string | null {
  const live = useLive()
  const cases = useCurrentVerification(live).cases
  return useMemo(() => {
    const text = live.currentText()
    const start = caseState.caseInfo.location.range.start
    const next = cases
      .map((candidate) => {
        return candidate.caseInfo.location.range.start
      })
      .filter((position) => {
        return position.line > start.line
      })
      .sort((first, second) => {
        return first.line - second.line
      })[0]
    return text === null ? null : propertyText(languageId, text, start, next ?? null)
  }, [live, cases, caseState, languageId])
}

function TraceOfCase({ caseState, flavor, readOnlyTabs }: TraceSlotProps): React.JSX.Element | null {
  const live = useLive()
  const runtime = useRuntime(live)
  const language = traceLanguageOf(flavor.languageId)
  const [activeId, setActiveId] = useState(language.views[0].id)
  const [expandedStep, setExpandedStep] = useState<number | null>(null)
  const property = useProperty(caseState, flavor.languageId)
  const { trace } = caseState
  if (!trace) {
    return null
  }
  const row = caseRowOf(caseState)
  const view = language.views.find((candidate) => {
    return candidate.id === activeId
  }) ?? language.views[0]
  const { View } = view
  return (
    <div className={`case-trace status-${row.status}`} data-view={view.id}>
      <div className="case-trace-head">
        <RunDetails caseState={caseState} />
        <ViewSelector
          views={language.views}
          active={view.id}
          choose={(id) => {
            setActiveId(id)
            setExpandedStep(null)
          }}
        />
      </div>
      <div className={`case-trace-body is-${view.layout}${row.outdated ? ' is-outdated' : ''}`}>
        {row.outdated
          ? (
            <div className="case-trace-outdated">
              <Icon name="edit" size="small" />
              The model changed since this trace. Press Verify all to check it again.
            </div>
          )
          : null}
        <View
          trace={trace}
          caseName={row.name}
          verdict={row.status}
          property={property}
          expandedStep={expandedStep}
          expandStep={setExpandedStep}
          runtime={runtime}
          openTab={readOnlyTabs === true
            ? (document) => {
              live.tabs.open(document)
            }
            : null}
        />
      </div>
    </div>
  )
}

// a newly selected case opens on the language's own view
export function CaseTrace(slot: TraceSlotProps): React.JSX.Element {
  return <TraceOfCase key={slot.caseState.caseInfo.id} {...slot} />
}
