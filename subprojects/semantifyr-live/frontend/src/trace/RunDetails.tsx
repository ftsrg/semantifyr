/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import type { VerificationMetrics } from '@semantifyr/editor-common'

import { useStore } from '../lib/stores/useStore'
import { isoDurationToMs } from '../lib/util/duration'
import type { VerificationCaseState } from '../lib/verification/engine'
import { formatSeconds } from '../lib/workspace/caseRows'
import { useLive } from '../lib/workspace/liveContext'
import { portfolioName } from '../lib/workspace/portfolioChoices'

const ENGINE_NAMES: Readonly<Record<string, string>> = {
  theta: 'Theta',
  spin: 'Spin',
  nuxmv: 'nuXmv',
  uppaal: 'Uppaal',
}

// a backend is named by its engine, the part of its id before the configuration
function engineOf(backendId: string | undefined): string | null {
  if (!backendId) {
    return null
  }
  const engine = backendId.split(':')[0] ?? backendId
  return ENGINE_NAMES[engine] ?? engine
}

function runLine(metrics: VerificationMetrics | undefined, portfolio: string, backendId: string | undefined): string {
  const parts = [
    metrics ? formatSeconds(isoDurationToMs(metrics.totalDuration)) : null,
    portfolio,
  ]
  const engine = engineOf(backendId)
  return [...parts, engine === null ? null : `decided by ${engine}`]
    .filter((part) => {
      return part !== null
    })
    .join(', ')
}

function validationLine(caseState: VerificationCaseState, portfolio: (id: string | null | undefined) => string, chosen: string): string {
  if (caseState.validating === true) {
    return `${caseState.validationServerState === 'Running' ? 'validating' : 'queued'}, ${portfolio(chosen)}`
  }
  switch (caseState.witnessValidation) {
    case undefined:
    case 'cancelled':
      return 'not run'
    case 'valid':
      return `validated, ${runLine(caseState.validationMetrics, portfolio(caseState.validationPortfolioIdUsed), caseState.validationBackendId)}`
    default:
      return `validation failed, ${runLine(caseState.validationMetrics, portfolio(caseState.validationPortfolioIdUsed), caseState.validationBackendId)}`
  }
}

export function RunDetails({ caseState }: { caseState: VerificationCaseState }): React.JSX.Element {
  const live = useLive()
  const portfolios = useStore(live.catalog, (catalog) => {
    return catalog.portfolios
  })
  const chosen = useStore(live.settings, (settings) => {
    return settings.validationPortfolioId
  })
  const portfolio = (id: string | null | undefined): string => {
    return portfolioName(portfolios, id)
  }
  return (
    <div className="run-details" data-testid="run-details">
      <span className="run-line">
        <span className="run-label">Verification</span>
        <span>{runLine(caseState.metrics, portfolio(caseState.portfolioId), caseState.backendId)}</span>
      </span>
      <span className="run-line">
        <span className="run-label">Validation</span>
        <span>{validationLine(caseState, portfolio, chosen)}</span>
      </span>
    </div>
  )
}
