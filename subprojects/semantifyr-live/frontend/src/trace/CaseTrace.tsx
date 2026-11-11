/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { Fragment, useState } from 'react'

import { useStore } from '../lib/stores/useStore'
import { caseRowOf } from '../lib/workspace/caseRows'
import { useLive, useRuntime } from '../lib/workspace/liveContext'
import { useShell } from '../shells/shellContext'
import type { TraceSlotProps } from '../shells/traceSlot'
import { Icon } from '../ui/icons'
import { shownView, traceLanguageOf, type TraceView } from './registry'
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

function TraceOfCase({ caseState, flavor, readOnlyTabs }: TraceSlotProps): React.JSX.Element | null {
  const live = useLive()
  const shell = useShell()
  const runtime = useRuntime(live)
  const language = traceLanguageOf(flavor.languageId)
  const chosen = useStore(shell.traceViews, (views) => {
    return views.get(flavor.languageId)
  })
  const [expandedStep, setExpandedStep] = useState<number | null>(null)
  const { trace } = caseState
  if (!trace) {
    return null
  }
  const row = caseRowOf(caseState)
  const view = shownView(language, chosen, { trace, caseName: row.name, runtime })
  const { View } = view
  return (
    <div className={`case-trace status-${row.status}`} data-view={view.id}>
      <div className="case-trace-head">
        <RunDetails caseState={caseState} />
        <ViewSelector
          views={language.views}
          active={view.id}
          choose={(id) => {
            shell.traceViews.update((previous) => {
              return new Map([...previous, [flavor.languageId, id]])
            })
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

// a newly selected case opens on the view chosen for its language, its steps folded
export function CaseTrace(slot: TraceSlotProps): React.JSX.Element {
  return <TraceOfCase key={slot.caseState.caseInfo.id} {...slot} />
}
