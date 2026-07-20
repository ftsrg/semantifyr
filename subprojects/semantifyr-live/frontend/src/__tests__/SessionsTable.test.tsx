/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SessionsTable from '../components/admin/SessionsTable';
import { fakeSession } from './fixtures/sessions';

const FULL_UUID = '11111111-2222-3333-4444-555555555555';

describe('SessionsTable', () => {
  it('renders the empty placeholder when no sessions are active', () => {
    render(<SessionsTable sessions={[]} onCancelSession={() => {}} onCancelVerification={() => {}} />);
    expect(screen.getByText('No active sessions.')).toBeInTheDocument();
  });

  it('renders the flavor and shortened session id for each row', () => {
    render(
      <SessionsTable
        sessions={[fakeSession({ sessionId: FULL_UUID, flavorId: 'oxsts' })]}
        onCancelSession={() => {}}
        onCancelVerification={() => {}}
      />,
    );
    expect(screen.getByText('oxsts')).toBeInTheDocument();
    expect(screen.getByText('11111111…')).toBeInTheDocument();
  });

  it('cancel-session button forwards the full session id', async () => {
    const onCancelSession = vi.fn();
    render(
      <SessionsTable
        sessions={[fakeSession({ sessionId: FULL_UUID })]}
        onCancelSession={onCancelSession}
        onCancelVerification={() => {}}
      />,
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Kill session' }));
    expect(onCancelSession).toHaveBeenCalledWith(FULL_UUID);
  });

  it('expanded row surfaces the in-flight verifications and forwards the cancel-verification ids', async () => {
    const onCancelVerification = vi.fn();
    render(
      <SessionsTable
        sessions={[
          fakeSession({
            sessionId: FULL_UUID,
            activeVerifications: [
              { verificationId: 'req-99', portfolioId: 'theta', kind: 'Verify', state: 'Running', elapsed: 'PT0S' },
            ],
          }),
        ]}
        onCancelSession={() => {}}
        onCancelVerification={onCancelVerification}
      />,
    );
    const user = userEvent.setup();
    await user.click(screen.getByText('oxsts'));
    expect(screen.getByText('In-flight verifications')).toBeInTheDocument();
    expect(screen.getByText('#req-99')).toBeInTheDocument();

    // The cancel button inside the expanded row carries the verb-only "Cancel" label; the
    // session-level destructor uses "Kill session" so we can disambiguate.
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancelVerification).toHaveBeenCalledWith('req-99');
  });
});
