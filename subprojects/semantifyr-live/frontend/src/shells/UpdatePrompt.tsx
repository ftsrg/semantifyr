/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useState } from 'react'
import { useRegisterSW } from 'virtual:pwa-register/react'

import { isPageOutdated } from '../lib/util/outdated'
import { Icon } from '../ui/icons'

const UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000

// the service worker as it is; only its prompt to reload after a deploy is drawn here
export function UpdatePrompt(): React.JSX.Element | null {
  const [outdated, setOutdated] = useState(false)
  useRegisterSW({
    onNeedReload: () => {
      isPageOutdated().then(setOutdated, (error: unknown) => {
        console.warn('semantifyr-live: could not check for a new version', error)
      })
    },
    onRegisteredSW: (_url, registration) => {
      if (registration) {
        setInterval(() => {
          void registration.update()
        }, UPDATE_CHECK_INTERVAL_MS)
      }
    },
    onRegisterError: (error) => {
      console.warn('semantifyr-live: service worker registration failed', error)
    },
  })
  if (!outdated) {
    return null
  }
  return (
    <div className="toast" role="status">
      <Icon name="refresh" size="small" />
      <span>A new version of Live is available.</span>
      <button
        type="button"
        className="button button-primary button-small"
        onClick={() => {
          window.location.reload()
        }}
      >
        Reload
      </button>
      <button
        type="button"
        className="button button-quiet button-small"
        onClick={() => {
          setOutdated(false)
        }}
      >
        Later
      </button>
    </div>
  )
}
