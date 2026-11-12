/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LiveFlavor } from '../../examples'
import { createServedLanguage } from './servedLanguage'
import { createSysmlLanguage } from './sysmlLanguage'
import type { LanguageUnit } from './types'

type LanguageFactory = (id: string, flavors: readonly LiveFlavor[]) => LanguageUnit

// Languages whose server runs in the page; every other language is served by the backend
const CLIENT_SIDE_LANGUAGES: Readonly<Record<string, LanguageFactory>> = {
  sysml: createSysmlLanguage,
}

export interface LanguageRegistry {
  units: readonly LanguageUnit[]
  // in the order the flavors were given
  flavors: readonly [LiveFlavor, ...LiveFlavor[]]
  unitOf: (flavorId: string) => LanguageUnit | undefined
  flavorOf: (flavorId: string | null | undefined) => LiveFlavor | undefined
}

export function createLanguageRegistry(flavors: readonly [LiveFlavor, ...LiveFlavor[]]): LanguageRegistry {
  const flavorsByLanguage = new Map<string, LiveFlavor[]>()
  for (const flavor of flavors) {
    const grouped = flavorsByLanguage.get(flavor.languageId) ?? []
    grouped.push(flavor)
    flavorsByLanguage.set(flavor.languageId, grouped)
  }
  const units = [...flavorsByLanguage].map(([languageId, grouped]) => {
    const factory = CLIENT_SIDE_LANGUAGES[languageId] ?? createServedLanguage
    return factory(languageId, grouped)
  })
  return {
    units,
    flavors,
    unitOf: (flavorId) => {
      return units.find((unit) => {
        return unit.flavors.some((flavor) => {
          return flavor.id === flavorId
        })
      })
    },
    flavorOf: (flavorId) => {
      return flavors.find((flavor) => {
        return flavor.id === flavorId
      })
    },
  }
}
