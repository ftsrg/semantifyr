/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActiveVerificationsMonitor from '../components/verification/ActiveVerificationsMonitor';
import type { ActiveVerificationInfo } from '../lib/api/types';
import type { SemantifyrLiveApi } from '../lib/api/lspExtensions';

function fakeApi(items: ActiveVerificationInfo[]): SemantifyrLiveApi {
  return {
    listVerifications: vi.fn(async () => ({ active: items })),
    onVerificationsChanged: () => () => {},
    cancelVerification: vi.fn(async () => true),
    cancelAllVerifications: vi.fn(async () => 0),
  } as unknown as SemantifyrLiveApi;
}

describe('ActiveVerificationsMonitor', () => {
  it('renders the badge as invisible when nothing is running', () => {
    const onActivate = vi.fn();
    render(<ActiveVerificationsMonitor api={fakeApi([])} connected={false} onActivate={onActivate} />);
    expect(screen.getByLabelText('Open Verifications panel')).toBeInTheDocument();
  });

  it('shows the live count badge when the api reports active items', async () => {
    const onActivate = vi.fn();
    render(
      <ActiveVerificationsMonitor
        api={fakeApi([{ requestId: 'r1' }, { requestId: 'r2' }])}
        connected
        onActivate={onActivate}
      />,
    );
    // The badge surfaces the count as visible text. waitFor because useActiveVerifications
    // flushes the initial fetch on mount.
    await waitFor(() => {
      expect(screen.getByText('2')).toBeInTheDocument();
    });
  });

  it('activate handler fires on click', async () => {
    const onActivate = vi.fn();
    render(<ActiveVerificationsMonitor api={fakeApi([])} connected onActivate={onActivate} />);
    const user = userEvent.setup();
    await user.click(screen.getByLabelText('Open Verifications panel'));
    expect(onActivate).toHaveBeenCalledOnce();
  });
});
