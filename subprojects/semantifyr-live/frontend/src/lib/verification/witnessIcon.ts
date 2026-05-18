/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { WitnessValidationStatus } from './engine'

export type WitnessIconKind = 'simple' | 'valid' | 'inconclusive' | 'failed'

export interface WitnessIconDescriptor {
  kind: WitnessIconKind
  iconColor: string
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
