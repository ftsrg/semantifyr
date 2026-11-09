/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const fetchInfo = vi.fn();
const fetchAdminStatus = vi.fn();
const fetchAdminConfig = vi.fn();
const fetchPortfolios = vi.fn();
const fetchAdminUser = vi.fn();
const cancelSession = vi.fn();
const cancelVerification = vi.fn();

vi.mock('../lib/api', () => ({
  createApi: () => ({
    httpBase: 'https://test.example',
    fetchPortfolios,
    fetchFlavors: vi.fn(async () => []),
    fetchFlavor: vi.fn(async () => null),
    fetchInfo,
    fetchAdminStatus,
    fetchAdminConfig,
    fetchAdminUser,
    cancelSession,
    cancelVerification,
  }),
}));

import AdminPage from '../components/AdminPage';

const baseInfo = {
  uptime: 'PT5M',
  startedAt: '2026-05-13T14:22:01Z',
  version: '0.1.0',
  commit: 'abcdef',
  buildTime: '2026-05-09T00:00:00Z',
  activeSessions: 0,
  maxSessions: 32,
};

const baseConfig = {
  server: {
    port: 8080,
    pingPeriod: 'PT30S',
    pingTimeout: 'PT15S',
    webRootDirectory: null,
    wsHandshakesPerPeriod: 120,
    wsHandshakeRatePeriod: 'PT1M',
    maxWsFrameSize: 4 * 1024 * 1024,
  },
  sessionManager: {
    maxSessionsGlobal: 32,
    semanticLibrariesDirectory: null,
    rootWorkDirectory: '/var/lib/semantifyr-live',
  },
  verification: {
    concurrency: 8,
    timeout: 'PT30S',
    maxPerSession: 42,
  },
};

const baseStatus = {
  sessions: [],
};

describe('AdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchInfo.mockResolvedValue(baseInfo);
    fetchAdminStatus.mockResolvedValue(baseStatus);
    fetchAdminConfig.mockResolvedValue(baseConfig);
    fetchPortfolios.mockResolvedValue({ portfolios: [], defaultPortfolio: 'smart-full' });
    fetchAdminUser.mockResolvedValue('octocat');
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('opens the dashboard without a login form and names the signed-in user', async () => {
    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    expect(await screen.findByText('octocat')).toBeInTheDocument();
    expect(screen.getByText('/ Admin')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Sessions (0)' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Admin password')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument();
  });

  it('shows why the admin API refused instead of a user', async () => {
    const refused = new Error('Not signed in through the proxy');
    fetchAdminUser.mockRejectedValue(refused);
    fetchInfo.mockRejectedValue(refused);
    fetchAdminStatus.mockRejectedValue(refused);
    fetchAdminConfig.mockRejectedValue(refused);

    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    expect(await screen.findByText('Not signed in through the proxy')).toBeInTheDocument();
    expect(screen.queryByText('octocat')).not.toBeInTheDocument();
  });

  it('toggling pause flips the icon button label and stops further refreshes', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });

    const user = userEvent.setup();
    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    const pause = await screen.findByRole('button', { name: 'Pause auto-refresh' });

    const callsBeforeTick = fetchAdminStatus.mock.calls.length;
    await act(async () => {
      vi.advanceTimersByTime(1000);
    });
    expect(fetchAdminStatus.mock.calls.length).toBe(callsBeforeTick + 1);

    await user.click(pause);
    expect(screen.getByText('Paused')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Resume auto-refresh' })).toBeInTheDocument();

    const callsAtPause = fetchAdminStatus.mock.calls.length;
    await act(async () => {
      vi.advanceTimersByTime(5000);
    });
    expect(fetchAdminStatus.mock.calls.length).toBe(callsAtPause);
  });
});
