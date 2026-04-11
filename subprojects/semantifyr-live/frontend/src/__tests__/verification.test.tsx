/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { VerifyButton, RefreshButton } from '../components/verification';

describe('VerifyButton', () => {
  const noop = (): void => {};

  it('shows "Verify all cases" tooltip text when idle', () => {
    render(<VerifyButton busy={false} disabled={false} onVerify={noop} onCancel={noop} />);
    expect(screen.getByLabelText('Run verification')).toBeInTheDocument();
  });

  it('shows "Cancel verification" label when busy', () => {
    render(<VerifyButton busy={true} disabled={false} onVerify={noop} onCancel={noop} />);
    expect(screen.getByRole('button', { name: 'Cancel verification' })).toBeInTheDocument();
  });

  it('calls onVerify when clicked in idle state', async () => {
    const onVerify = vi.fn();
    render(<VerifyButton busy={false} disabled={false} onVerify={onVerify} onCancel={noop} />);
    await userEvent.setup().click(screen.getByRole('button'));
    expect(onVerify).toHaveBeenCalledOnce();
  });

  it('calls onCancel when clicked in busy state', async () => {
    const onCancel = vi.fn();
    render(<VerifyButton busy={true} disabled={false} onVerify={noop} onCancel={onCancel} />);
    await userEvent.setup().click(screen.getByRole('button'));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it('disables the button when disabled and not busy', () => {
    render(<VerifyButton busy={false} disabled={true} onVerify={noop} onCancel={noop} />);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('does not disable the button when busy even if disabled is true', () => {
    render(<VerifyButton busy={true} disabled={true} onVerify={noop} onCancel={noop} />);
    expect(screen.getByRole('button')).not.toBeDisabled();
  });
});

describe('RefreshButton', () => {
  it('renders with the correct label', () => {
    render(<RefreshButton disabled={false} onClick={() => {}} />);
    expect(screen.getByRole('button', { name: 'Refresh cases' })).toBeInTheDocument();
  });

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn();
    render(<RefreshButton disabled={false} onClick={onClick} />);
    await userEvent.setup().click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('disables the button when disabled', () => {
    render(<RefreshButton disabled={true} onClick={() => {}} />);
    expect(screen.getByRole('button')).toBeDisabled();
  });
});
