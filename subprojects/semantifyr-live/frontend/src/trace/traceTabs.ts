/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useEffect } from 'react'

import type { LanguageRuntime } from '../lib/language/types'
import type { VerificationTrace } from '@semantifyr/editor-common'
import { caseRowOf } from '../lib/workspace/caseRows'
import type { Live } from '../lib/workspace/live'
import { traceLanguageOf } from './registry'
import { readText, traceTabOf } from './TextView'

// an open trace text shows the last trace of the case it was opened for, and closes once that case has none or another document is shown
export function followTraceTabs(live: Live, runtime: LanguageRuntime, languageId: string): () => void {
  const views = traceLanguageOf(languageId).views
  const reading = new Map<string, VerificationTrace>()

  const follow = (): void => {
    const { documentKey } = live.workspace.get()
    for (const tab of live.tabs.state.get().tabs) {
      const followed = tab.trace
      if (!followed) {
        continue
      }
      const caseState = followed.documentKey === documentKey
        ? live.verification.get().get(documentKey)?.cases.find((candidate) => {
          return candidate.caseInfo.id === followed.caseId
        })
        : undefined
      const trace = caseState?.trace
      const view = views.find((candidate) => {
        return candidate.id === followed.view
      })
      const source = caseState && trace && view?.source ? view.source({ trace, caseName: caseRowOf(caseState).name, runtime }) : null
      if (!trace || !source) {
        reading.delete(tab.id)
        live.tabs.close(tab.id)
        continue
      }
      if (trace === followed.shown || reading.get(tab.id) === trace) {
        continue
      }
      reading.set(tab.id, trace)
      void readText(runtime, source.fileUri).then((text) => {
        if (reading.get(tab.id) !== trace) {
          return
        }
        reading.delete(tab.id)
        const open = live.tabs.state.get().tabs.find((candidate) => {
          return candidate.id === tab.id
        })
        if (open?.trace?.caseId !== followed.caseId || open.trace.documentKey !== followed.documentKey) {
          return
        }
        if (text === null) {
          live.tabs.close(tab.id)
          return
        }
        live.tabs.replace(traceTabOf(source, text, { ...followed, shown: trace }))
      })
    }
  }

  const subscriptions = [
    live.verification.subscribe(follow),
    live.workspace.subscribe(follow),
  ]
  follow()
  return () => {
    for (const unsubscribe of subscriptions) {
      unsubscribe()
    }
  }
}

export function useFollowedTraceTabs(live: Live, runtime: LanguageRuntime | null, languageId: string | null): void {
  useEffect(() => {
    if (!runtime || languageId === null) {
      return
    }
    return followTraceTabs(live, runtime, languageId)
  }, [live, runtime, languageId])
}
