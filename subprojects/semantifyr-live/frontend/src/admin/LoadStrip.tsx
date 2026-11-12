/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import type { AdminLoadResponse } from '../lib/api'
import { durationLabel } from './adminView'

interface FigureProps {
  label: string
  value: number | string
  against?: string
  testId: string
}

function Figure({ label, value, against, testId }: FigureProps): React.JSX.Element {
  return (
    <div className="admin-figure" data-testid={testId}>
      <span className="section-label">{label}</span>
      <span className="admin-figure-value">
        {value}
        {against === undefined ? null : <span className="admin-figure-against">{` ${against}`}</span>}
      </span>
    </div>
  )
}

export function LoadStrip({ load }: { load: AdminLoadResponse }): React.JSX.Element {
  return (
    <section className="admin-load" aria-label="Load">
      <Figure testId="load-sessions" label="Sessions" value={load.activeSessions} against={`of ${load.maxSessions}`} />
      <Figure testId="load-running" label="Verifications running" value={load.runningVerifications} against={`of ${load.verificationPermits} permits`} />
      <Figure testId="load-queued" label="Verifications queued" value={load.queuedVerifications} against={load.queuedVerifications === 0 ? '' : 'waiting for a permit'} />
      <Figure testId="load-uptime" label="Backend uptime" value={durationLabel(load.uptime)} />
    </section>
  )
}
