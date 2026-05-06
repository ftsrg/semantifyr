/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ProblemEntry } from '../components/LiveEditor'
import type { VerificationCaseState, VerificationCaseStatus } from './verification'

/**
 * Map a {@link VerificationCaseStatus} to the Monaco/VS Code marker severity it should render
 * on the `@VerificationCase` line. Returns `null` for non-terminal statuses (queued, running,
 * stale) so the editor stays clean while a run is in progress.
 */
export function caseStatusToSeverity(
  status: VerificationCaseStatus,
): 'error' | 'warning' | null {
  switch (status) {
    case 'failed':
    case 'errored':
      return 'error'
    case 'inconclusive':
    case 'not_supported':
      return 'warning'
    default:
      return null
  }
}

/**
 * Default human-readable message attached to the Monaco marker when the verifier did not
 * supply one. Kept in sync with the verdict copy used in the verification-panel tooltip.
 */
export function defaultMessageForStatus(
  status: VerificationCaseStatus,
  label: string,
): string {
  switch (status) {
    case 'failed':
      return `${label}: property fails on at least one execution. See the witness pane.`
    case 'errored':
      return `${label}: verification raised an error. Check the verification panel for details.`
    case 'inconclusive':
      return `${label}: the verifier could not produce a decisive verdict. Try a different portfolio.`
    case 'not_supported':
      return `${label}: the chosen portfolio cannot transform this case. Pick another.`
    default:
      return label
  }
}

/** Build the marker list (one per terminal-status case) the editor should render. */
export function casesToMarkers(
  cases: readonly VerificationCaseState[],
): ProblemEntry[] {
  const markers: ProblemEntry[] = []
  for (const cs of cases) {
    const severity = caseStatusToSeverity(cs.status)
    if (severity === null) {
      continue
    }
    markers.push({
      severity,
      startLine: cs.caseInfo.location.range.start.line,
      startColumn: cs.caseInfo.location.range.start.character,
      endLine: cs.caseInfo.location.range.end.line,
      endColumn: cs.caseInfo.location.range.end.character,
      message: cs.message ?? defaultMessageForStatus(cs.status, cs.caseInfo.label),
      source: 'semantifyr-verify',
    })
  }
  return markers
}
