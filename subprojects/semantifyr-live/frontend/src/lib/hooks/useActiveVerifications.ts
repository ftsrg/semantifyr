/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useCallback, useEffect, useState } from 'react'
import type { ActiveVerificationInfo } from '../api/types'
import type { SemantifyrLiveApi } from '../api/lspExtensions'

export interface ActiveVerificationsController {
  items: ActiveVerificationInfo[]
  cancel: (verificationId: string) => Promise<void>
  cancelAll: () => Promise<void>
  refresh: () => Promise<void>
}

export function useActiveVerifications(
  api: SemantifyrLiveApi | null,
  connected: boolean,
): ActiveVerificationsController {
  const [items, setItems] = useState<ActiveVerificationInfo[]>([])

  const refresh = useCallback(async () => {
    if (!connected || !api) {
      return
    }
    try {
      const result = await api.listVerifications()
      setItems(result.active ?? [])
    } catch {
      /* the request is intercepted before the LSP server sees it; transient connection drops fall through */
    }
  }, [api, connected])

  useEffect(() => {
    if (!connected || !api) {
      return
    }
    void refresh()
    const dispose = api.onVerificationsChanged((params) => {
      setItems(params.active ?? [])
    })
    return () => {
      dispose()
    }
  }, [api, connected, refresh])

  const cancel = useCallback(async (verificationId: string) => {
    if (!api) {
      return
    }
    try {
      await api.cancelVerification(verificationId)
    } catch {
      /* ignore - the changed notification will refresh us */
    }
  }, [api])

  const cancelAll = useCallback(async () => {
    if (!api) {
      return
    }
    try {
      await api.cancelAllVerifications()
    } catch {
      /* ignore - the changed notification will refresh us */
    }
  }, [api])

  return { items, cancel, cancelAll, refresh }
}
