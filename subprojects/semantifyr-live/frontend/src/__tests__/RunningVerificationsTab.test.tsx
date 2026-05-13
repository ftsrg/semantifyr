/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RunningVerificationsTab from '../components/verification/RunningVerificationsTab';
import type { ActiveVerificationInfo } from '../lib/api/types';
import type { SemantifyrLiveApi } from '../lib/api/lspExtensions';
import type { VerificationCaseState } from '../lib/verification';

function fakeApi(items: ActiveVerificationInfo[]): {
  api: SemantifyrLiveApi;
  cancelVerification: ReturnType<typeof vi.fn>;
  cancelAllVerifications: ReturnType<typeof vi.fn>;
} {
  const cancelVerification = vi.fn(async () => true);
  const cancelAllVerifications = vi.fn(async () => items.length);
  const api = {
    listVerifications: vi.fn(async () => ({ active: items })),
    onVerificationsChanged: () => () => {},
    cancelVerification,
    cancelAllVerifications,
  } as unknown as SemantifyrLiveApi;
  return { api, cancelVerification, cancelAllVerifications };
}

const sampleCase = (id: string, status: VerificationCaseState['status']): VerificationCaseState => ({
  caseInfo: {
    id,
    label: `Case ${id}`,
    location: {
      uri: 'inmemory:///snippet.oxsts',
      range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
    },
  },
  status,
  metrics: {
    totalDuration: 'PT1S',
    preparationDuration: 'PT0.1S',
    verificationDuration: 'PT0.8S',
    backAnnotationDuration: 'PT0S',
  },
});

describe('RunningVerificationsTab', () => {
  it('shows the empty placeholder for active verifications when nothing is running', async () => {
    const { api } = fakeApi([]);
    render(
      <RunningVerificationsTab
        api={api}
        connected
        cases={[]}
        portfolios={[]}
      />,
    );
    expect(await screen.findByText('No running verifications or validations.')).toBeInTheDocument();
    expect(screen.getByText('No completed verifications yet.')).toBeInTheDocument();
  });

  it('renders one row per active verification, labels it by the matching case, and forwards cancel ids', async () => {
    const aLocation = {
      uri: 'inmemory:///snippet.oxsts',
      range: { start: { line: 1, character: 0 }, end: { line: 1, character: 0 } },
    };
    const { api, cancelVerification, cancelAllVerifications } = fakeApi([
      { verificationId: 'r1', kind: 'Verify', portfolioId: 'smart-full', elapsed: 'PT0S', location: aLocation },
      { verificationId: 'r2', kind: 'Validate', portfolioId: 'smart-full', elapsed: 'PT0S' },
    ]);
    render(
      <RunningVerificationsTab
        api={api}
        connected
        cases={[{ caseInfo: { id: 'A', label: 'Case A', location: aLocation }, status: 'queued' }]}
        portfolios={[]}
      />,
    );
    await waitFor(() => {
      expect(screen.getByText('Case A')).toBeInTheDocument();
    });
    expect(screen.getByText('#r2')).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByLabelText('Cancel Case A'));
    expect(cancelVerification).toHaveBeenCalledWith('r1');

    await user.click(screen.getByLabelText('Cancel all active verifications'));
    expect(cancelAllVerifications).toHaveBeenCalled();
  });

  it('summary section reports counts plus wall-clock + average across timed cases', async () => {
    const { api } = fakeApi([]);
    render(
      <RunningVerificationsTab
        api={api}
        connected
        cases={[
          sampleCase('a', 'passed'),
          sampleCase('b', 'failed'),
          sampleCase('c', 'inconclusive'),
        ]}
        portfolios={[]}
      />,
    );
    expect(await screen.findByText('Total')).toBeInTheDocument();
    expect(screen.getByText('Passed')).toBeInTheDocument();
    expect(screen.getByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('Inconclusive')).toBeInTheDocument();
    expect(screen.getByText('Wall-clock')).toBeInTheDocument();
    expect(screen.getByText('Average')).toBeInTheDocument();
  });
});
