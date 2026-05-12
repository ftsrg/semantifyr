/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import WitnessTab from '../components/witness/WitnessTab';
import { sampleCase } from './fixtures/cases';
import { sampleTrace as trace } from './fixtures/verification-results';

const caseInfo = sampleCase('P1');

describe('WitnessTab', () => {
  it('renders the case label, the validation chip, and a revalidate button when validation has run', () => {
    render(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation="valid"
        validating={false}
        canRevalidate
        onRevalidate={() => {}}
        editorHandle={null}
        witnessLanguageId="oxsts"
      />,
    );
    expect(screen.getByText(caseInfo.label)).toBeInTheDocument();
    expect(screen.getByText('Witness valid')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Validate witness' })).toBeInTheDocument();
  });

  it('shows the verifying portfolio caption when supplied', () => {
    render(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation={undefined}
        validating={false}
        canRevalidate={false}
        onRevalidate={() => {}}
        verificationPortfolioLabel="Theta"
        editorHandle={null}
        witnessLanguageId="oxsts"
      />,
    );
    expect(screen.getByText('Theta')).toBeInTheDocument();
  });

  it('forwards onRevalidate clicks', async () => {
    const onRevalidate = vi.fn();
    render(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation="valid"
        validating={false}
        canRevalidate
        onRevalidate={onRevalidate}
        editorHandle={null}
        witnessLanguageId="oxsts"
      />,
    );
    await userEvent.setup().click(screen.getByRole('button', { name: 'Validate witness' }));
    expect(onRevalidate).toHaveBeenCalledOnce();
  });

  it('disables the revalidate button when canRevalidate=false', () => {
    render(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation="valid"
        validating={false}
        canRevalidate={false}
        onRevalidate={() => {}}
        editorHandle={null}
        witnessLanguageId="oxsts"
      />,
    );
    expect(screen.getByRole('button', { name: 'Validate witness' })).toBeDisabled();
  });

  it('shows the "validating..." chip label while a validation is in flight', () => {
    render(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation={undefined}
        validating
        canRevalidate
        onRevalidate={() => {}}
        editorHandle={null}
        witnessLanguageId="oxsts"
      />,
    );
    expect(screen.getByText('Validating...')).toBeInTheDocument();
  });

  it('toggles between Trace, State, and Raw views', async () => {
    render(
      <WitnessTab
        caseInfo={caseInfo}
        trace={trace}
        validation={undefined}
        validating={false}
        canRevalidate={false}
        onRevalidate={() => {}}
        editorHandle={null}
        witnessLanguageId="oxsts"
      />,
    );
    const user = userEvent.setup();
    // Default view is Trace.
    expect(screen.getByRole('button', { name: 'Trace' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'State' }));
    expect(screen.getByRole('button', { name: 'State' })).toHaveAttribute('aria-pressed', 'true');
  });
});
