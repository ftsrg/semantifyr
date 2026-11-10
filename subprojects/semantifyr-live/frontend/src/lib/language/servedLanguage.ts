/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LiveFlavor } from '../../examples'
import { InitializationSteps } from '../stores/initialization'
import { createServedRuntime } from './servedRuntime'
import type { LanguageUnit } from './types'

const SERVED_STEPS = [
  InitializationSteps.editor,
  InitializationSteps.connect,
  InitializationSteps.cases,
]

// A language whose server is the backend: everything it needs is in the flavor records
export function createServedLanguage(id: string, flavors: readonly LiveFlavor[]): LanguageUnit {
  return {
    id,
    flavors,
    start: (flavor, context) => {
      return createServedRuntime({
        backendUrl: context.backendUrl,
        flavor,
        socketFlavorId: flavor.id,
        clientLanguageId: flavor.languageId,
        colorMode: context.colorMode,
        steps: SERVED_STEPS,
      })
    },
  }
}
