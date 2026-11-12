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
  // a shell with read-only tabs; without them no view offers to open as one
  readOnlyTabs?: boolean
  // a text view is as tall as its text, for a frame that grows with it
  growing?: boolean
  // the lines of a text view wrap instead of scrolling sideways
  wrapping?: boolean
}

// What the trace area shows under the selected case's name; the shell draws the area and the name
export type TraceSlot = React.ComponentType<TraceSlotProps>
