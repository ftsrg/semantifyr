/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useState } from 'react'

import { createApi } from '../lib/api'
import { createSettingsStore, createSystemColorModeStore, resolveColorMode } from '../lib/stores/settings'
import { useStore } from '../lib/stores/useStore'
import { createAdminStore } from './adminStore'
import { deploymentLinks } from './adminView'
import { AdminPage } from './AdminPage'

export default function AdminApp(): React.JSX.Element {
  const [settings] = useState(createSettingsStore)
  const [system] = useState(createSystemColorModeStore)
  const [store] = useState(() => {
    return createAdminStore(createApi(window.location.origin))
  })
  const theme = useStore(settings, (state) => {
    return state.theme
  })
  const systemMode = useStore(system)
  return (
    <AdminPage
      store={store}
      settings={settings}
      colorMode={resolveColorMode(theme, systemMode)}
      links={deploymentLinks(window.location)}
    />
  )
}
