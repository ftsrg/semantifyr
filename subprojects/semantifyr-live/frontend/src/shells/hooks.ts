/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useEffect, useState } from 'react'

import { presentationOf, type LanguagePresentation } from '../lib/language/presentation'
import { useStore } from '../lib/stores/useStore'
import type { DocumentRecord, Live } from '../lib/workspace/live'
import type { LiveFlavor } from '../examples'

export function useCurrentDocument(live: Live): DocumentRecord | null {
  const key = useStore(live.workspace, (workspace) => {
    return workspace.documentKey
  })
  return useStore(live.documents, (documents) => {
    return key === null ? null : documents.get(key) ?? null
  })
}

export function useFlavor(live: Live): LiveFlavor | null {
  return useStore(live.workspace, (workspace) => {
    return workspace.flavor
  })
}

export interface CurrentLanguage {
  id: string
  presentation: LanguagePresentation
}

export function useLanguage(live: Live): CurrentLanguage | null {
  const flavor = useFlavor(live)
  return flavor ? { id: flavor.languageId, presentation: presentationOf(flavor.languageId) } : null
}

// the time, ticking while asked to
export function useNow(intervalMs: number | null): number {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    if (intervalMs === null) {
      return
    }
    setNow(Date.now())
    const timer = setInterval(() => {
      setNow(Date.now())
    }, intervalMs)
    return () => {
      clearInterval(timer)
    }
  }, [intervalMs])
  return now
}

// the file name the model is shown under, after its example
export function documentLabel(document: DocumentRecord, flavor: LiveFlavor): string {
  const extension = flavor.fileName.slice(flavor.fileName.lastIndexOf('.'))
  const base = document.shared ? 'shared' : document.example.id.replace(/_/g, '-')
  return `${base}${extension}`
}
