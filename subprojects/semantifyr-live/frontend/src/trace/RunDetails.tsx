/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { ReportKind, type SoundnessDirection, soundnessLabel } from '@semantifyr/editor-common'

import { useStore } from '../lib/stores/useStore'
import { isoDurationToMs } from '../lib/util/duration'
import type { VerificationCaseState } from '../lib/verification/engine'
import { formatSeconds, waitedPhrase } from '../lib/workspace/caseRows'
import { useLive } from '../lib/workspace/liveContext'
import { portfolioName } from '../lib/workspace/portfolioChoices'
import { BACKEND_NAMES } from '../texts'

// a backend is named by the part of its id before the configuration
function backendNameOf(backendId: string | null | undefined): string | null {
  if (!backendId) {
    return null
  }
  const backend = backendId.split(':')[0] ?? backendId
  return BACKEND_NAMES[backend] ?? backend
}

function runLine(
  totalDuration: string | undefined,
  portfolio: string,
  backendId: string | null | undefined,
  waited: number | undefined,
  soundness?: SoundnessDirection,
): string {
  const backend = backendNameOf(backendId)
  return [
    totalDuration === undefined ? null : formatSeconds(isoDurationToMs(totalDuration)),
    waitedPhrase(waited),
    portfolio,
    backend === null ? null : `decided by ${backend}`,
    soundness === undefined ? null : soundnessLabel(soundness),
  ]
    .filter((part) => {
      return part !== null
    })
    .join(', ')
}

function validationRunLine(caseState: VerificationCaseState, portfolio: (id: string | null | undefined) => string): string {
  const result = caseState.validationReport?.result
  return runLine(result?.metrics.totalDuration, portfolio(caseState.validationPortfolioIdUsed), result?.metadata.backendId, caseState.validationWaited, result?.soundness)
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
      return `validated, ${validationRunLine(caseState, portfolio)}`
    default:
      return `validation failed, ${validationRunLine(caseState, portfolio)}`
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
        <span className="run-label">{caseState.trace?.kind === ReportKind.Recheck ? 'Validation' : 'Verification'}</span>
        <span>{runLine(caseState.metrics?.totalDuration, portfolio(caseState.portfolioId), caseState.backendId, caseState.waited, caseState.soundness)}</span>
      </span>
      <span className="run-line">
        <span className="run-label">Validation</span>
        <span>{validationLine(caseState, portfolio, chosen)}</span>
      </span>
    </div>
  )
}
