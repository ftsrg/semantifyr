/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import MappedOxstsTab from '../components/witness/MappedOxstsTab'
import type { DerivedDocument } from '../lib/language/types'
import { SYSML_OXSTS_URI } from '../lib/session/sysmlBridge'

function mapped(text: string | null): DerivedDocument {
  return { id: 'oxsts', label: 'OXSTS', uri: SYSML_OXSTS_URI, languageId: 'oxsts', text }
}

describe('MappedOxstsTab', () => {
  it('opens the mapped OXSTS read-only and follows the mapping', async () => {
    const openReadonlyEditor = vi.fn(() => Promise.resolve({ dispose: () => {} }))
    const { rerender } = render(<MappedOxstsTab document={mapped('first')} openReadonlyEditor={openReadonlyEditor} />)

    await vi.waitFor(() => {
      expect(openReadonlyEditor).toHaveBeenCalledTimes(1)
    })
    expect(openReadonlyEditor).toHaveBeenCalledWith(expect.anything(), SYSML_OXSTS_URI, 'oxsts')

    rerender(<MappedOxstsTab document={mapped('second')} openReadonlyEditor={openReadonlyEditor} />)

    await vi.waitFor(() => {
      expect(openReadonlyEditor).toHaveBeenCalledTimes(2)
    })
  })

  it('says why there is no OXSTS', () => {
    const openReadonlyEditor = vi.fn(() => Promise.resolve({ dispose: () => {} }))
    render(<MappedOxstsTab document={mapped(null)} openReadonlyEditor={openReadonlyEditor} />)

    expect(screen.getByText(/No OXSTS/)).toBeTruthy()
    expect(openReadonlyEditor).not.toHaveBeenCalled()
  })
})
