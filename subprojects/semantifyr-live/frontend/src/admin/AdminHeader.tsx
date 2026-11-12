/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import { changeSettings, type Settings } from '../lib/stores/settings'
import type { Store } from '../lib/stores/store'
import { useStore } from '../lib/stores/useStore'
import type { ColorModePreference } from '../lib/util/colorMode'
import { Icon, type IconName } from '../ui/icons'
import { Logo } from '../ui/marks'
import { AdminIcon } from './adminIcons'
import type { AdminStore } from './adminStore'
import type { DeploymentLinks } from './adminView'

const THEME_CHOICES: readonly { value: ColorModePreference, icon: IconName, label: string }[] = [
  { value: 'system', icon: 'monitor', label: 'System' },
  { value: 'light', icon: 'sun', label: 'Light' },
  { value: 'dark', icon: 'moon', label: 'Dark' },
]

function ThemeSwitch({ settings }: { settings: Store<Settings> }): React.JSX.Element {
  const theme = useStore(settings, (state) => {
    return state.theme
  })
  return (
    <div className="segmented segmented-small" role="group" aria-label="Theme">
      {THEME_CHOICES.map((choice) => {
        return (
          <button
            key={choice.value}
            type="button"
            className="segmented-option"
            aria-pressed={theme === choice.value}
            title={choice.label}
            aria-label={choice.label}
            onClick={() => {
              changeSettings(settings, { theme: choice.value })
            }}
          >
            <Icon name={choice.icon} size="small" />
          </button>
        )
      })}
    </div>
  )
}

interface AdminHeaderProps {
  store: AdminStore
  settings: Store<Settings>
  links: DeploymentLinks
}

export function AdminHeader({ store, settings, links }: AdminHeaderProps): React.JSX.Element {
  const paused = useStore(store.state, (state) => {
    return state.paused
  })
  const refreshedAt = useStore(store.state, (state) => {
    return state.refreshedAt
  })
  const user = useStore(store.state, (state) => {
    return state.user
  })
  const pauseLabel = paused ? 'Resume auto-refresh' : 'Pause auto-refresh'
  return (
    <header className="admin-header">
      <div className="admin-header-group">
        <Logo />
        <span className="separator" />
        <h1 className="admin-title">Admin</h1>
      </div>
      <div className="admin-header-group">
        <span className="admin-refreshed" data-testid="admin-refresh-state">
          {paused ? 'Paused' : refreshedAt === null ? '' : `Updated ${new Date(refreshedAt).toLocaleTimeString()}`}
        </span>
        {refreshedAt === null
          ? null
          : (
            <button
              type="button"
              className="button button-icon"
              aria-label={pauseLabel}
              title={pauseLabel}
              aria-pressed={paused}
              onClick={store.togglePause}
            >
              {paused ? <Icon name="play" size="small" /> : <AdminIcon name="pause" />}
            </button>
          )}
        {links.grafana === null
          ? null
          : (
            <a className="button button-quiet" href={links.grafana} target="_blank" rel="noreferrer">
              Grafana
              <Icon name="external" size="small" />
            </a>
          )}
        <ThemeSwitch settings={settings} />
        {user === null
          ? null
          : (
            <>
              <span className="separator" />
              <span className="admin-user">
                Signed in as
                {' '}
                <strong>{user}</strong>
              </span>
              {links.signOut === null
                ? null
                : (
                  <a className="button button-quiet" href={links.signOut}>
                    <AdminIcon name="sign-out" />
                    Sign out
                  </a>
                )}
            </>
          )}
      </div>
    </header>
  )
}
