/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { formatIsoDurationDetailed } from './duration'
import type { VerificationMetrics as WireMetrics } from '@semantifyr/editor-common'

/**
 * Tooltip metrics input. Both verification and witness-validation runs return the same
 * {@link WireMetrics} stages (compile, verify, back-annotate); the helper renders whichever
 * subset has a meaningful (non-zero) duration. Optional fields let callers reuse this for
 * partial responses without forcing every backend to fill every field.
 */
export type TooltipMetrics = Partial<WireMetrics>

export interface MetricsTooltipOptions {
  /** Backend portfolio that ran the work (e.g. "Theta", "Smart"). Rendered when present. */
  portfolioLabel?: string | undefined
  /** Engine identifier that produced the verdict (e.g. backend internal id). */
  backendId?: string | undefined
}

/** ISO 8601 zero durations from kotlin.time.Duration come through as "PT0S". */
export function isMeaningfulDuration(iso: string | undefined | null): boolean {
  return typeof iso === 'string' && iso.length > 0 && iso !== 'PT0S'
}

function appendStage(
  parts: string[],
  label: string,
  value: string | undefined | null,
): void {
  if (isMeaningfulDuration(value)) {
    parts.push(`${label}: ${formatIsoDurationDetailed(value!)}`)
  }
}

/**
 * Render a stage breakdown identical for verification and witness-validation runs. The
 * narrative stages (compilation -> verification -> back-annotation) match the engine's actual
 * pipeline; the optional portfolio / backend lines tell the user which portfolio ran the work
 * so the witness pill matches the verify pill at a glance.
 */
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
