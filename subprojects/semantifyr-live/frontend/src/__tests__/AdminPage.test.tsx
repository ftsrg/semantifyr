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
  startedAt: '2026-05-13T14:22:01Z',
  commit: 'abcdef',
  buildTime: '2026-05-09T00:00:00Z',
  activeSessions: 0,
  maxSessions: 32,
};

const baseConfig = {
  development: false,
  server: {
    port: 8080,
    pingPeriod: 'PT30S',
    pingTimeout: 'PT15S',
    webRootDirectory: null,
    adminPasswordSet: true,
    wsHandshakesPerPeriod: 120,
    wsHandshakeRatePeriod: 'PT1M',
    maxWsFrameSize: 4 * 1024 * 1024,
    httpsOnlyCookies: true,
  },
  sessionManager: {
    maxSessionsGlobal: 32,
    semanticLibrariesDirectory: null,
    rootWorkDirectory: '/var/lib/semantifyr-live',
  },
  verification: {
    concurrency: 8,
    timeout: 'PT30S',
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
    const signOut = await screen.findByRole('button', { name: 'Sign out' });
    await user.click(signOut);
    expect(logoutAdmin).toHaveBeenCalled();
    await screen.findByText('Semantifyr Admin');
  });

  it('toggling pause flips the icon button label and stops further refreshes', async () => {
    checkAdminAuth.mockResolvedValue(true);

    const user = userEvent.setup();
    render(<AdminPage colorModePreference="dark" colorMode="dark" onToggleColorMode={() => {}} />);
    const pause = await screen.findByRole('button', { name: 'Pause auto-refresh' });

    await waitFor(() => {
      expect(fetchAdminStatus).toHaveBeenCalled();
    });
    const callsBeforePause = fetchAdminStatus.mock.calls.length;

    await user.click(pause);
    expect(screen.getByText('Paused')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Resume auto-refresh' })).toBeInTheDocument();

    expect(fetchAdminStatus.mock.calls.length).toBe(callsBeforePause);
  });
});
