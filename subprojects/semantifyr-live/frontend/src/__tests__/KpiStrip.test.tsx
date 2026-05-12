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
  it('renders sessions, verifications, errors, and uptime tiles', () => {
    render(
      <KpiStrip
        uptime="PT1H30M"
        activeSessions={3}
        maxSessions={10}
        sessions={[
          fakeSession({ sessionId: 's1', flavorId: 'oxsts', activeVerifications: [{ requestId: 'r1' }] }),
          fakeSession({ sessionId: 's2', flavorId: 'gamma' }),
          fakeSession({ sessionId: 's3', flavorId: 'oxsts', bridgeInfo: { clientMessageCount: 1, serverMessageCount: 1, errorCount: 2, timeSinceLastClientMessage: 'PT0S', timeSinceLastServerMessage: 'PT0S' } }),
        ]}
      />,
    );
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('/ 10')).toBeInTheDocument();
    // active verifications across sessions = 1
    expect(screen.getByText('Active verifications')).toBeInTheDocument();
    expect(screen.getAllByText('1').length).toBeGreaterThan(0);
    // errors total = 2
    expect(screen.getByText('Errors (cumulative)')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    // flavor breakdown chips
    expect(screen.getByText('oxsts 2')).toBeInTheDocument();
    expect(screen.getByText('gamma 1')).toBeInTheDocument();
  });

  it('reports Idle and No errors when nothing is active', () => {
    render(
      <KpiStrip
        uptime="PT1S"
        activeSessions={0}
        maxSessions={10}
        sessions={[]}
      />,
    );
    expect(screen.getByText('Idle')).toBeInTheDocument();
    expect(screen.getByText('No errors recorded')).toBeInTheDocument();
  });
});
