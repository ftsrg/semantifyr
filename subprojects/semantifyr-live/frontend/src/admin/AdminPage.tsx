/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect } from 'react'

import type { Settings } from '../lib/stores/settings'
import type { Store } from '../lib/stores/store'
import { useStore } from '../lib/stores/useStore'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import { Icon } from '../ui/icons'
import { AdminHeader } from './AdminHeader'
import type { AdminError, AdminStore } from './adminStore'
import type { DeploymentLinks } from './adminView'
import { Deployed } from './Deployed'
import { LoadStrip } from './LoadStrip'
import { Sessions } from './Sessions'

import '@semantifyr/editor-common/tokens.css'
import '@semantifyr/editor-common/marks.css'
import '../ui/components.css'
import '../ui/base.css'
import './admin.css'

function refusal(error: AdminError): { title: string, detail: string } {
  if (error.status === 403) {
    return { title: 'Not signed in', detail: 'The admin API answers only to a user the proxy in front of the server signed in. Reload the page to sign in.' }
  }
  if (error.status === 401) {
    return { title: 'Signed out', detail: 'The sign-in has expired. Reload the page to sign in again.' }
  }
  return { title: 'The server did not answer', detail: error.message }
}

function Refused({ error, store }: { error: AdminError, store: AdminStore }): React.JSX.Element {
  const { title, detail } = refusal(error)
  return (
    <div className="admin-refused" role="alert" data-testid="admin-refused">
      <Icon name="lock" />
      <h2 className="popover-title">{title}</h2>
      <p>{detail}</p>
      <button
        type="button"
        className="button button-outline"
        onClick={() => {
          void store.refresh()
        }}
      >
        <Icon name="refresh" size="small" />
        Try again
      </button>
    </div>
  )
}

interface AdminPageProps {
  store: AdminStore
  settings: Store<Settings>
  colorMode: ResolvedColorMode
  links: DeploymentLinks
}

export function AdminPage({ store, settings, colorMode, links }: AdminPageProps): React.JSX.Element {
  useEffect(() => {
    document.documentElement.dataset.theme = colorMode
    document.title = 'Admin - Semantifyr Live'
  }, [colorMode])
  useEffect(() => {
    return store.start()
  }, [store])
  const data = useStore(store.state, (state) => {
    return state.data
  })
  const error = useStore(store.state, (state) => {
    return state.error
  })
  const notice = useStore(store.state, (state) => {
    return state.notice
  })
  const refreshedAt = useStore(store.state, (state) => {
    return state.refreshedAt
  })
  return (
    <div className="admin">
      <AdminHeader store={store} settings={settings} links={links} />
      {data === null
        ? error === null
          ? <div className="progress-indeterminate" role="progressbar" aria-label="Loading" />
          : <Refused error={error} store={store} />
        : (
          <main className="admin-main">
            {error === null
              ? null
              : (
                <div className="admin-stale" role="alert" data-testid="admin-stale">
                  <Icon name="alert" size="small" />
                  {`Could not refresh: ${error.message}. Showing the state of ${refreshedAt === null ? 'the last refresh' : new Date(refreshedAt).toLocaleTimeString()}.`}
                </div>
              )}
            <LoadStrip load={data.status.load} />
            <div className="admin-columns">
              <Sessions store={store} />
              <Deployed store={store} />
            </div>
          </main>
        )}
      {notice === null
        ? null
        : (
          <div className="toast" role="status">
            <Icon name="info" size="small" />
            {notice}
          </div>
        )}
    </div>
  )
}
