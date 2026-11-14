/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import { encodeCompressedBase64Url } from '@semantifyr/editor-common'

import type { LspClient } from '../../lib/verification/engine'
import { servedFlavors } from '../fixtures/flavors'
import { createTestLive, type TestLive } from '../helpers/live'

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
  return {}
})

const silent: LspClient = {
  sendRequest: async () => {
    return null
  },
  sendNotification: () => {},
}

describe('a page that moves to another address in place', () => {
  let open: TestLive | null = null

  afterEach(async () => {
    await open?.live.dispose()
    open = null
  })

  it('opens the model the new address names', async () => {
    open = createTestLive(silent, servedFlavors)
    open.live.start('/oxsts/intersection')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })

    open.live.openAddress(`/oxsts#model=${await encodeCompressedBase64Url('package linked')}`)

    await vi.waitFor(() => {
      expect(open!.live.currentText()).toBe('package linked')
    })
    expect(open.live.documents.get().get(open.live.workspace.get().documentKey!)?.shared).toBe(true)
  })
})
