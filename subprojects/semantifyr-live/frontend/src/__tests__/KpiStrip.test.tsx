/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import KpiStrip from '../components/admin/KpiStrip';
import { fakeSession } from './fixtures/sessions';

describe('KpiStrip', () => {
  it('renders sessions, verifications, and uptime tiles', () => {
    render(
      <KpiStrip
        uptime="PT1H30M"
        startedAt="2026-05-13T13:00:00Z"
        activeSessions={3}
        maxSessions={10}
        verificationConcurrency={4}
        sessions={[
          fakeSession({
            sessionId: 's1',
            flavorId: 'oxsts',
            activeVerifications: [{ verificationId: 'r1', portfolioId: 'p1', kind: 'Verify', state: 'Running', elapsed: 'PT0S' }],
          }),
          fakeSession({ sessionId: 's2', flavorId: 'gamma' }),
          fakeSession({ sessionId: 's3', flavorId: 'oxsts' }),
        ]}
      />,
    );
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('/ 10')).toBeInTheDocument();
    expect(screen.getByText('Active verifications')).toBeInTheDocument();
    expect(screen.getAllByText('1').length).toBeGreaterThan(0);
    expect(screen.getByText('oxsts 2')).toBeInTheDocument();
    expect(screen.getByText('gamma 1')).toBeInTheDocument();
  });

  it('reports Idle when nothing is active', () => {
    render(
      <KpiStrip
        uptime="PT1S"
        startedAt="2026-05-13T13:00:00Z"
        activeSessions={0}
        maxSessions={10}
        verificationConcurrency={4}
        sessions={[]}
      />,
    );
    expect(screen.getByText(/^Idle/)).toBeInTheDocument();
  });
});
