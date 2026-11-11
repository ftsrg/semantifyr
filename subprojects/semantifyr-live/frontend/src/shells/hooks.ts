/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useEffect, useState } from 'react'
import { decodeCompressedBase64Url } from '@semantifyr/editor-common'

import { presentationOf, type LanguagePresentation } from '../lib/language/presentation'
import { SYSML_FLAVOR_INFO } from '../lib/session/sysmlBridge'
import { useStore } from '../lib/stores/useStore'
import { addressedModel, readAddress, type AddressedModel } from '../lib/workspace/address'
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
  if (document.file) {
    return document.file.name
  }
  const extension = flavor.fileName.slice(flavor.fileName.lastIndexOf('.'))
  const base = document.shared ? 'shared' : document.example.id.replace(/_/g, '-')
  return `${base}${extension}`
}

export interface WaitingModel extends AddressedModel {
  // null while a model the address carries is read
  text: string | null
}

// the model the address names, while the server has not answered with its languages
export function useAddressedModel(live: Live): WaitingModel {
  const location = useStore(live.requested) ?? window.location.href
  const addressed = addressedModel(readAddress(location), SYSML_FLAVOR_INFO.languageId)
  const [decoded, setDecoded] = useState<{ compressed: string, text: string | null } | null>(null)
  const compressed = addressed.compressedModel
  useEffect(() => {
    if (compressed === null) {
      return
    }
    let current = true
    void decodeCompressedBase64Url(compressed).then((text) => {
      if (current) {
        setDecoded({ compressed, text })
      }
    })
    return () => {
      current = false
    }
  }, [compressed])
  const model = compressed !== null && decoded?.compressed === compressed ? decoded.text : null
  return { ...addressed, text: model ?? addressed.example?.code ?? (compressed === null ? '' : null) }
}
