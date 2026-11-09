/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { act, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import MappedOxstsTab from '../components/witness/MappedOxstsTab'
import type { LiveEditorHandle } from '../components/editor/LiveEditor'
import { SYSML_OXSTS_URI } from '../lib/session/sysmlBridge'

function createHandle(oxsts: string | null) {
  let text = oxsts
  let notify = (): void => {}
  const attachReadonlyEditor = vi.fn(() => Promise.resolve({ dispose: () => {} }))
  const handle = {
    getMappedOxsts: () => text,
    addMappingListener: (listener: () => void) => {
      notify = listener
      return () => {}
    },
    attachReadonlyEditor,
  } as unknown as LiveEditorHandle
  return {
    handle,
    attachReadonlyEditor,
    change: (next: string | null) => {
      text = next
      notify()
    },
  }
}

describe('MappedOxstsTab', () => {
  it('opens the mapped OXSTS read-only and follows the mapping', async () => {
    const { handle, attachReadonlyEditor, change } = createHandle('first')
    render(<MappedOxstsTab editorHandle={handle} />)

    await vi.waitFor(() => {
      expect(attachReadonlyEditor).toHaveBeenCalledTimes(1)
    })
    expect(attachReadonlyEditor).toHaveBeenCalledWith(expect.anything(), SYSML_OXSTS_URI, 'oxsts')

    act(() => {
      change('second')
    })

    await vi.waitFor(() => {
      expect(attachReadonlyEditor).toHaveBeenCalledTimes(2)
    })
  })

  it('says why there is no OXSTS', () => {
    const { handle, attachReadonlyEditor } = createHandle(null)
    render(<MappedOxstsTab editorHandle={handle} />)

    expect(screen.getByText(/No OXSTS/)).toBeTruthy()
    expect(attachReadonlyEditor).not.toHaveBeenCalled()
  })
})
