/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { Popover } from '@base-ui/react/popover'

import { hostOf } from '../../lib/language/presentation'
import { isInitialized } from '../../lib/stores/initialization'
import { useStore } from '../../lib/stores/useStore'
import { connectionView, type CloudLook } from '../../lib/workspace/connectionView'
import { useLive } from '../../lib/workspace/liveContext'
import { Cloud, type CloudState } from '../../ui/marks'
import { useLanguage } from '../hooks'

const CLOUDS: Readonly<Record<CloudLook, CloudState>> = {
  connected: 'connected',
  busy: 'busy',
  connecting: 'connecting',
  offline: 'offline',
  lost: 'failure',
  failure: 'failure',
}

const CLASSES: Readonly<Record<CloudLook, string>> = {
  connected: 'is-connected',
  busy: 'is-connecting is-busy',
  connecting: 'is-connecting',
  offline: 'is-offline',
  lost: 'is-lost',
  failure: 'is-failure',
}

export function ConnectionIndicator(): React.JSX.Element {
  const live = useLive()
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const connection = useStore(live.connection)
  const initialization = useStore(live.initialization)
  const language = useLanguage(live)
  const view = connectionView(catalog, connection, isInitialized(initialization), hostOf(window.location.origin), language?.presentation.name ?? 'language')

  const act = (): void => {
    switch (view.action) {
      case 'disconnect':
        void live.disconnect()
        break
      case 'reconnect':
        live.reconnect()
        break
      case 'restart':
        live.restartSession()
        break
      case null:
        break
    }
  }

  return (
    <Popover.Root>
      <Popover.Trigger className={`connection ${CLASSES[view.look]}`} title={view.title} aria-label={view.title}>
        <Cloud state={CLOUDS[view.look]} />
        <span>Live</span>
        {view.reason !== null ? <span className="connection-reason">{view.reason}</span> : null}
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner className="floating" side="bottom" align="start" sideOffset={6}>
          <Popover.Popup className="menu connection-popover">
            <div className="connection-row">
              <Cloud state={CLOUDS[view.look] === 'busy' ? 'connected' : CLOUDS[view.look]} />
              <Popover.Title className="popover-title">{view.heading}</Popover.Title>
            </div>
            <Popover.Description className="connection-detail">{view.detail}</Popover.Description>
            {view.action !== null
              ? (
                <Popover.Close className="button button-outline button-small" onClick={act}>
                  {view.actionLabel}
                </Popover.Close>
              )
              : null}
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}
