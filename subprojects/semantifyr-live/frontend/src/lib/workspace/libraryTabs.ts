/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { SYSML_LIBRARY_PREFIX } from '../language/libraries'
import type { LanguageRuntime } from '../language/types'
import { derivedUriOf, fileNameOf, fileUriOfLibrary, LIBRARY_SCHEME, libraryUriOf } from '../session/readOnlyDocuments'
import type { ReadOnlyDocument } from './documentTabs'
import type { Live } from './live'

export const MODEL_OXSTS_TAB_ID = 'model-oxsts'

const SEMANTIC_LIBRARY_BANNERS: Readonly<Record<string, string>> = {
  'States.oxsts': 'How Semantifyr reads SysML v2 state machines: part of its semantic library, in OXSTS.',
  'Statecharts.oxsts': 'How Semantifyr reads Gamma statecharts: part of its semantic library, in OXSTS.',
}

function bannerOf(fileUri: string): string {
  const fileName = fileNameOf(fileUri)
  if (fileUri.startsWith(SYSML_LIBRARY_PREFIX)) {
    return 'From the SysML v2 standard library.'
  }
  if (fileUri.startsWith('file:///workspace/.semantifyr/')) {
    return 'The OXSTS library every model builds on.'
  }
  return SEMANTIC_LIBRARY_BANNERS[fileName] ?? 'Part of Semantifyr\'s semantic library, in OXSTS.'
}

// KerML files of the SysML library read as SysML, which the editor knows
function languageOf(fileName: string, languageId: string): string {
  return fileName.endsWith('.kerml') || fileName.endsWith('.sysml') ? 'sysml' : languageId
}

// A library file as a read-only tab; the editor reads it by its URI, under a scheme no language client syncs
export function libraryTabOf(uri: string, languageId: string): ReadOnlyDocument {
  const fileUri = uri.startsWith(`${LIBRARY_SCHEME}:`) ? fileUriOfLibrary(uri) : uri
  const libraryUri = libraryUriOf(fileUri)
  return {
    id: libraryUri,
    label: decodeURIComponent(fileNameOf(fileUri)),
    uri: libraryUri,
    languageId: languageOf(fileNameOf(fileUri), languageId),
    banner: bannerOf(fileUri),
  }
}

export function modelOxstsTabOf(modelLabel: string, text: string): ReadOnlyDocument {
  const label = modelLabel.replace(/\.[^.]*$/, '.oxsts')
  return {
    id: MODEL_OXSTS_TAB_ID,
    label,
    uri: derivedUriOf(MODEL_OXSTS_TAB_ID, label),
    languageId: 'oxsts',
    banner: `The OXSTS that ${modelLabel} compiles to. It follows your edits.`,
    text,
  }
}

export function modelOxstsText(text: string | null, message: string | null | undefined): string {
  return text ?? `// ${message ?? 'The OXSTS shows once the model compiles.'}\n`
}

export function openModelOxsts(live: Live, runtime: LanguageRuntime, modelLabel: string): void {
  const document = runtime.modelOxsts.get()
  live.tabs.open(modelOxstsTabOf(modelLabel, modelOxstsText(document?.text ?? null, document?.message)))
}
