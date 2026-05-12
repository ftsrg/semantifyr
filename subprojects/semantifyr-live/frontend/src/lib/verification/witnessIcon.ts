/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { WitnessValidationStatus } from './engine'

export type WitnessIconKind = 'simple' | 'valid' | 'inconclusive' | 'failed'

/**
 * Visual descriptor for the per-row "Show witness" button. Four user-visible states:
 *
 * - `simple` - no validation has run yet (auto-validate off, or pending). Default muted icon.
 * - `valid` - validation reproduced the verdict. Quiet success-green dot overlay.
 * - `inconclusive` - validator gave up (timeout, abstraction loss). Warning-yellow dot;
 *   the wire `errored` status is folded in here too because both mean "can't trust this".
 * - `failed` - validation portfolio disagreed (the witness fails to replay). Danger-red dot.
 */
export interface WitnessIconDescriptor {
  kind: WitnessIconKind
  iconColor: string
  /** MUI Badge color name; the consumer maps it via the theme. */
  badgeColor: 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'
  badgeVisible: boolean
  tooltip: string
  ariaLabel: string
}

export function witnessIconDescriptor(
  status: WitnessValidationStatus | undefined,
): WitnessIconDescriptor {
  switch (status) {
    case 'valid':
      return {
        kind: 'valid',
        iconColor: 'var(--text-muted)',
        badgeColor: 'success',
        badgeVisible: true,
        tooltip: 'Show witness (validation passed)',
        ariaLabel: 'Show witness, validation passed',
      }
    case 'inconclusive':
    case 'errored':
      return {
        kind: 'inconclusive',
        iconColor: 'var(--warning)',
        badgeColor: 'warning',
        badgeVisible: true,
        tooltip: 'Show witness (validation inconclusive)',
        ariaLabel: 'Show witness, validation inconclusive',
      }
    case 'invalid':
      return {
        kind: 'failed',
        iconColor: 'var(--danger)',
        badgeColor: 'error',
        badgeVisible: true,
        tooltip: 'Show witness (validation failed)',
        ariaLabel: 'Show witness, validation failed',
      }
    default:
      return {
        kind: 'simple',
        iconColor: 'var(--text-muted)',
        badgeColor: 'default',
        badgeVisible: false,
        tooltip: 'Show witness',
        ariaLabel: 'Show witness',
      }
  }
}
