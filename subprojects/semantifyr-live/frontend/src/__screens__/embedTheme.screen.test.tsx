/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { describe, expect, it, onTestFinished, vi } from 'vitest'

import ShellApp from '../shells/ShellApp'

vi.mock('../shells/UpdatePrompt', () => {
  return {
    UpdatePrompt: () => {
      return null
    },
  }
})

// Goal: an embedded frame shows the colour mode its address names, whatever the system prefers, and stores nothing of it

describe('the embed of a framing page with a colour mode', () => {
  for (const theme of ['dark', 'light'] as const) {
    it(`shows the ${theme} theme the address names`, async () => {
      // a seeded test of the same browser may have stored a colour mode
      window.localStorage.removeItem('semantifyr-live-color-mode')
      const address = `${window.location.pathname}${window.location.search}`
      window.history.replaceState(null, '', `${window.location.pathname}?view=embed&theme=${theme}`)
      const container = document.createElement('div')
      document.body.append(container)
      const root = createRoot(container)
      onTestFinished(() => {
        root.unmount()
        container.remove()
        window.history.replaceState(null, '', address)
      })
      root.render(<ShellApp />)
      await expect.poll(() => {
        return document.querySelector('[data-testid=embed-shell]')
      }, { timeout: 60_000 }).toBeTruthy()
      expect(document.documentElement.dataset.theme).toBe(theme)
      expect(window.localStorage.getItem('semantifyr-live-color-mode')).toBeNull()
    })
  }
})
