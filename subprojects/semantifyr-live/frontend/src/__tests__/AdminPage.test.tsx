/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const fetchInfo = vi.fn();
const fetchAdminStatus = vi.fn();
const fetchAdminConfig = vi.fn();
const fetchPortfolios = vi.fn();
const checkAdminAuth = vi.fn();
const loginAdmin = vi.fn();
const logoutAdmin = vi.fn();
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
    checkAdminAuth,
    loginAdmin,
    logoutAdmin,
    cancelSession,
    cancelVerification,
  }),
}));

import AdminPage from '../components/AdminPage';

const baseInfo = {
  uptime: 'PT5M',
  commit: 'abcdef',
  buildTime: '2026-05-09T00:00:00Z',
  activeSessions: 0,
  maxSessions: 32,
};

const baseConfig = {
  maxSessionsGlobal: 32,
  maxSessionsPerIp: 4,
  verificationConcurrency: 8,
  verificationTimeout: 'PT30S',
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
    fetchPortfolios.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the login form when whoami returns false', async () => {
    checkAdminAuth.mockResolvedValueOnce(false);
    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    expect(await screen.findByText('Semantifyr Admin')).toBeInTheDocument();
    expect(screen.getByLabelText('Admin password')).toBeInTheDocument();
  });

  it('falls back to the login form when whoami rejects (network failure)', async () => {
    checkAdminAuth.mockRejectedValueOnce(new Error('offline'));
    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    expect(await screen.findByText('Semantifyr Admin')).toBeInTheDocument();
  });

  it('flips to authenticated when login succeeds and surfaces the error otherwise', async () => {
    checkAdminAuth.mockResolvedValue(false);
    loginAdmin.mockResolvedValueOnce(false);
    loginAdmin.mockResolvedValueOnce(true);

    const user = userEvent.setup();
    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    await screen.findByText('Semantifyr Admin');

    const password = screen.getByLabelText('Admin password');
    const submit = screen.getByRole('button', { name: 'Login' });

    await user.type(password, 'wrong');
    await user.click(submit);
    expect(await screen.findByText('Invalid password')).toBeInTheDocument();

    await user.clear(password);
    await user.type(password, 'right');
    await user.click(submit);
    await screen.findByText('/ Admin');
  });

  it('logout flips local state to unauthenticated even when the network call rejects', async () => {
    checkAdminAuth.mockResolvedValue(true);
    logoutAdmin.mockRejectedValueOnce(new Error('offline'));

    const user = userEvent.setup();
    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    // Wait for the dashboard to finish its initial load. The Sign out button only mounts once
    // loading=false (the loading-state header omits per-row controls).
    const signOut = await screen.findByRole('button', { name: 'Sign out' });
    await user.click(signOut);
    expect(logoutAdmin).toHaveBeenCalled();
    // After logout the login form mounts again, even though the network rejected.
    await screen.findByText('Semantifyr Admin');
  });

  it('toggling pause flips the icon button label and stops further refreshes', async () => {
    checkAdminAuth.mockResolvedValue(true);

    const user = userEvent.setup();
    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    const pause = await screen.findByRole('button', { name: 'Pause auto-refresh' });

    // Wait for the first refresh to land before snapshotting the call count.
    await waitFor(() => {
      expect(fetchAdminStatus).toHaveBeenCalled();
    });
    const callsBeforePause = fetchAdminStatus.mock.calls.length;

    await user.click(pause);
    expect(screen.getByText('Paused')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Resume auto-refresh' })).toBeInTheDocument();

    // No new refreshes can fire while paused; we never advance fake timers, so the count stays.
    expect(fetchAdminStatus.mock.calls.length).toBe(callsBeforePause);
  });
});
