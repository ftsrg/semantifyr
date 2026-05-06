/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import WitnessTab from '../components/witness/WitnessTab'
import type {
  VerificationCaseSpecification,
  VerificationTrace,
} from '../lib/verification'

const caseInfo: VerificationCaseSpecification = {
  id: 'P1',
  label: 'P1',
  location: {
    uri: 'inmemory:///workspace/Foo.oxsts',
    range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
  },
}

const trace: VerificationTrace = {
  callTrace: { initialStep: { traces: [] }, steps: [] },
  witnessState: { initialStep: { values: [] }, steps: [] },
  backAnnotatedSource: '',
  witnessUri: 'inmemory:///workspace/Foo.witness.oxsts',
}

describe('WitnessTab close button', () => {
  it('renders the close button only when onClose is provided', () => {
    const { rerender } = render(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation={undefined}
        validating={false}
        canRevalidate={false}
        onRevalidate={() => {}}
      />,
    )
    expect(screen.queryByRole('button', { name: 'Close witness' })).toBeNull()

    rerender(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation={undefined}
        validating={false}
        canRevalidate={false}
        onRevalidate={() => {}}
        onClose={() => {}}
      />,
    )
    expect(screen.getByRole('button', { name: 'Close witness' })).toBeInTheDocument()
  })

  it('calls onClose when the close button is clicked', async () => {
    const onClose = vi.fn()
    render(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation={undefined}
        validating={false}
        canRevalidate={false}
        onRevalidate={() => {}}
        onClose={onClose}
      />,
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'Close witness' }))
    expect(onClose).toHaveBeenCalledOnce()
  })
})
