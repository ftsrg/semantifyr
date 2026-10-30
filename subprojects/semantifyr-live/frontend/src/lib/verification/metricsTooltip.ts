/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { formatIsoDurationDetailed } from '../util/duration'
import type { VerificationMetrics as WireMetrics } from '@semantifyr/editor-common'

export type TooltipMetrics = Partial<WireMetrics>

export interface MetricsTooltipOptions {
  portfolioLabel?: string | undefined
  backendId?: string | undefined
}

// "PT0S" is how kotlin.time.Duration encodes Duration.ZERO.
export function isMeaningfulDuration(iso: string | undefined | null): iso is string {
  return typeof iso === 'string' && iso.length > 0 && iso !== 'PT0S'
}

function appendStage(
  parts: string[],
  label: string,
  value: string | undefined | null,
): void {
  if (isMeaningfulDuration(value)) {
    parts.push(`${label}: ${formatIsoDurationDetailed(value)}`)
  }
}

export function buildMetricsTooltip(
  metrics: TooltipMetrics | undefined,
  options: MetricsTooltipOptions = {},
): string {
  if (!metrics) {
    return options.portfolioLabel ? `Portfolio: ${options.portfolioLabel}` : ''
  }
  const parts: string[] = []
  appendStage(parts, 'Compilation', metrics.preparationDuration)
  appendStage(parts, 'Verification', metrics.verificationDuration)
  appendStage(parts, 'Back-annotation', metrics.backAnnotationDuration)
  if (options.portfolioLabel) {
    parts.push(`Portfolio: ${options.portfolioLabel}`)
  }
  if (options.backendId) {
    parts.push(`Backend: ${options.backendId}`)
  }
  return parts.join('  ·  ')
}
