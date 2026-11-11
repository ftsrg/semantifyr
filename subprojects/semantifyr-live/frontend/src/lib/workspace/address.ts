/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { decodeCompressedBase64Url, decodeLiveVerdicts, parseLiveEditorUrl, type LiveEditorAddress } from '@semantifyr/editor-common'

import { bundledExamplesOf, findExample, type LiveExample, type LiveFlavor } from '../../examples'
import { LANGUAGE_ORDER } from '../language/presentation'
import type { LanguageRegistry } from '../language/registry'
import type { DocumentFile } from './files'
import type { AddressProblem } from './live'
import { verdictsOf, type SharedResults } from './sharedResults'

// the flavours of a language that add a library, by the library's name in the address
const LIBRARIES: Readonly<Record<string, string>> = {
  'oxsts-with-gamma-library': 'gamma',
  'oxsts-with-sysmlv2-library': 'sysml',
}

export function libraryOf(flavorId: string): string | undefined {
  return LIBRARIES[flavorId]
}

// what an address names, from what the page knows before the server answers: a language's plain flavor carries its id
export interface AddressedModel {
  languageId: string
  flavorId: string
  library: string | undefined
  examples: readonly LiveExample[]
  // null for a model the address carries, or for an example the build does not ship
  example: LiveExample | null
  compressedModel: string | null
}

export function addressedModel(address: LiveEditorAddress, defaultLanguageId: string): AddressedModel {
  const languageId = LANGUAGE_ORDER.find((language) => {
    return language === address.language
  }) ?? defaultLanguageId
  const withLibrary = Object.entries(LIBRARIES).find(([flavorId, library]) => {
    return library === address.library && flavorId.startsWith(`${languageId}-`)
  })
  const flavorId = withLibrary?.[0] ?? languageId
  const examples = bundledExamplesOf(flavorId)
  const compressedModel = address.compressedModel
  const example = compressedModel !== null && address.example === null ? null : findExample({ examples }, address.example) ?? examples[0] ?? null
  return { languageId, flavorId, library: withLibrary?.[1], examples, example, compressedModel }
}

export type HistoryChange =
  | 'push'
  | 'replace'

// A switch to an example is an entry of its own, so Back returns to the model before it; a file or a shared model takes the entry it opened in
export function historyChangeOf(current: string, next: string, shared: boolean): HistoryChange | null {
  const withoutFragment = (address: string): string => {
    return address.replace(/#.*$/, '')
  }
  if (withoutFragment(current) === withoutFragment(next)) {
    return null
  }
  return shared ? 'replace' : 'push'
}

export interface InitialDocument {
  flavor: LiveFlavor
  example: LiveExample
  text: string
  shared: boolean
  problem: AddressProblem | null
  file?: DocumentFile
  // a document opened from a file or a link replaces the shared one
  replace?: boolean
  results?: SharedResults
  // the case the link selected, when it carries no verdicts
  selectedCase?: string
}

export function readAddress(address: string): LiveEditorAddress {
  return parseLiveEditorUrl(new URL(address, 'http://live.invalid'))
}

// an embedded frame takes the colour mode of the page around it, which is never stored
export function frameColorMode(address: LiveEditorAddress): 'light' | 'dark' | null {
  return address.view === null ? null : address.theme
}

export function fixedFrame(address: LiveEditorAddress): boolean {
  return address.view !== null && address.frame === 'fixed'
}

function flavorOfLanguage(registry: LanguageRegistry, language: string, library: string | null): LiveFlavor | undefined {
  return registry.flavors.find((flavor) => {
    return flavor.languageId === language && libraryOf(flavor.id) === (library ?? undefined)
  })
}

// An unknown name opens what the address would have opened without it; the first unknown name is the one reported
export async function resolveAddress(address: LiveEditorAddress, registry: LanguageRegistry, defaultFlavorId: string | undefined): Promise<InitialDocument> {
  const defaultFlavor = registry.flavorOf(defaultFlavorId) ?? registry.flavors[0]
  const language = address.language === null ? undefined : flavorOfLanguage(registry, address.language, null)
  const withLibrary = language && address.library !== null ? flavorOfLanguage(registry, address.language ?? '', address.library) : undefined
  const flavor = withLibrary ?? language ?? defaultFlavor
  const fallback = flavor.examples[0]
  if (!fallback) {
    throw new Error(`flavor ${flavor.id} has no examples`)
  }
  const found = address.example === null ? undefined : findExample(flavor, address.example)
  const example = found ?? fallback
  const model = address.compressedModel === null ? null : await decodeCompressedBase64Url(address.compressedModel)
  const entries = address.compressedVerdicts === null ? null : await decodeLiveVerdicts(address.compressedVerdicts)
  const verdicts = entries === null ? null : verdictsOf(entries)
  const problemOf = (kind: AddressProblem['kind'], requested: string): AddressProblem => {
    return { kind, requested, flavorId: flavor.id, exampleId: example.id }
  }
  const problem = address.language !== null && !language
    ? problemOf('language', address.language)
    : address.library !== null && !withLibrary
      ? problemOf('library', address.library)
      : address.example !== null && !found
        ? problemOf('example', address.example)
        : address.compressedModel !== null && model === null
          ? problemOf('model', '')
          : address.compressedVerdicts !== null && verdicts === null
            ? problemOf('results', '')
            : null
  const results: SharedResults | null = verdicts === null ? null : { selectedCase: address.case, verdicts }
  // a model without an example in the path is a shared one; with one, it is that example edited
  const shared = model !== null && address.example === null
  return {
    flavor,
    example,
    text: model ?? example.code,
    shared,
    problem,
    ...(model === null ? {} : { replace: true }),
    ...(results === null ? {} : { results }),
    ...(results === null && address.case !== null ? { selectedCase: address.case } : {}),
  }
}
