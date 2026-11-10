/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import type { LiveFlavor } from '../examples'
import type { VerificationCaseState } from '../lib/verification/engine'

export interface TraceSlotProps {
  caseState: VerificationCaseState
  flavor: LiveFlavor
}

// What the trace area shows under the selected case's name; the shell draws the area and the name
export type TraceSlot = React.ComponentType<TraceSlotProps>
