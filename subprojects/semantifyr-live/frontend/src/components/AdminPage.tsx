/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import IconButton from '@mui/material/IconButton';
import Snackbar from '@mui/material/Snackbar';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Tooltip from '@mui/material/Tooltip';
import PauseCircleOutlinedIcon from '@mui/icons-material/PauseCircleOutlined';
import PlayCircleOutlinedIcon from '@mui/icons-material/PlayCircleOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import type { ColorModePreference, ResolvedColorMode } from '../lib/util/colorMode';
import AppHeader from './shell/AppHeader';
import ColorModeToggle from './shell/ColorModeToggle';
import KpiStrip from './admin/KpiStrip';
import SessionsTab from './admin/SessionsTab';
import ConfigTab from './admin/ConfigTab';
import {
  createApi,
  type AdminConfigResponse,
  type AdminStatusResponse,
  type FlavorInfo,
  type InfoResponse,
  type LiveServerApi,
  type PortfolioInfo,
} from '../lib/api';
import { resolveBackendUrl } from '../lib/util/backendUrl';
import { FONT_SIZE, ICON_SIZE } from '../lib/util/theme';

const REFRESH_INTERVAL_MS = 1000;

type AdminTab = 'sessions' | 'config';

interface AdminHeaderProps {
  lastRefreshAt: number | null;
  paused?: boolean;
  onTogglePause?: () => void;
  onLogout?: () => void;
  preference: ColorModePreference;
  colorMode: ResolvedColorMode;
  onToggleColorMode: () => void;
}

function AdminHeader({
  lastRefreshAt,
  paused,
  onTogglePause,
  onLogout,
  preference,
  colorMode,
  onToggleColorMode,
}: AdminHeaderProps): React.JSX.Element {
  const logoSrc = colorMode === 'dark' ? '/logo-full-dark.svg' : '/logo-full-light.svg';
  return (
    <AppHeader logoSrc={logoSrc}>
      <Typography sx={{ color: 'text.secondary', fontSize: FONT_SIZE.md }}>/ Admin</Typography>
      <Box sx={{ flex: 1 }} />
      {lastRefreshAt !== null && (
        <Typography
          sx={{
            fontSize: FONT_SIZE.xs,
            color: 'text.secondary',
            display: { xs: 'none', sm: 'inline' },
          }}
        >
          {paused ? 'Paused' : `Updated ${new Date(lastRefreshAt).toLocaleTimeString()}`}
        </Typography>
      )}
      {onTogglePause && (
        <Tooltip title={paused ? 'Resume auto-refresh' : 'Pause auto-refresh'}>
          <IconButton
            size="small"
            onClick={onTogglePause}
            aria-label={paused ? 'Resume auto-refresh' : 'Pause auto-refresh'}
            sx={{ color: 'text.secondary' }}
          >
            {paused ? (
              <PlayCircleOutlinedIcon sx={{ fontSize: ICON_SIZE.lg }} />
            ) : (
              <PauseCircleOutlinedIcon sx={{ fontSize: ICON_SIZE.lg }} />
            )}
          </IconButton>
        </Tooltip>
      )}
      <ColorModeToggle preference={preference} onToggle={onToggleColorMode} />
      {onLogout && (
        <Tooltip title="Sign out">
          <IconButton size="small" onClick={onLogout} aria-label="Sign out" sx={{ color: 'text.secondary' }}>
            <LogoutIcon sx={{ fontSize: ICON_SIZE.lg }} />
          </IconButton>
        </Tooltip>
      )}
    </AppHeader>
  );
}

function LoginForm({ onLogin, error }: { onLogin: (password: string) => void; error: boolean }): React.JSX.Element {
  const [password, setPassword] = useState('');
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100dvh',
        bgcolor: 'var(--page-bg)',
        gap: 2,
      }}
    >
      <Typography variant="h5" sx={{ mb: 1 }}>Semantifyr Admin</Typography>
      {error && <Alert severity="error">Invalid password</Alert>}
      <Box
        component="form"
        onSubmit={(e: React.SyntheticEvent) => {
          e.preventDefault();
          onLogin(password);
        }}
        sx={{ display: 'flex', gap: 1 }}
      >
        <TextField
          type="password"
          label="Admin password"
          size="small"
          value={password}
          onChange={(e) => { setPassword(e.target.value); }}
          autoFocus
          sx={{
            '& .MuiInputBase-root': { color: 'text.primary', bgcolor: 'var(--surface-bg)' },
            '& .MuiInputLabel-root': { color: 'text.secondary' },
            '& .MuiOutlinedInput-notchedOutline': { borderColor: 'var(--surface-border)' },
          }}
        />
        <Button type="submit" variant="contained" color="primary">
          Login
        </Button>
      </Box>
    </Box>
  );
}

interface DashboardData {
  info: InfoResponse;
  admin: AdminStatusResponse;
  config: AdminConfigResponse;
}

interface ToastState {
  message: string;
  severity: 'success' | 'error' | 'info';
}

interface DashboardProps {
  api: LiveServerApi;
  onLogout: () => void;
  preference: ColorModePreference;
  colorMode: ResolvedColorMode;
  onToggleColorMode: () => void;
}

function Dashboard({ api, onLogout, preference, colorMode, onToggleColorMode }: DashboardProps): React.JSX.Element {
  const [data, setData] = useState<DashboardData | null>(null);
  const [portfolios, setPortfolios] = useState<readonly PortfolioInfo[]>([]);
  const [flavors, setFlavors] = useState<readonly FlavorInfo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastRefreshAt, setLastRefreshAt] = useState<number | null>(null);
  const [paused, setPaused] = useState(false);
  const [filter, setFilter] = useState('');
  const [toast, setToast] = useState<ToastState | null>(null);
  const [tab, setTab] = useState<AdminTab>('sessions');
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [info, admin, config] = await Promise.all([
        api.fetchInfo(),
        api.fetchAdminStatus(),
        api.fetchAdminConfig(),
      ]);
      setData({ info, admin, config });
      setError(null);
      setLastRefreshAt(Date.now());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    void refresh();
    void api.fetchPortfolios().then(setPortfolios).catch(() => { setPortfolios([]); });
    void api.fetchFlavors().then(setFlavors).catch(() => { setFlavors([]); });
  }, [refresh, api]);

  useEffect(() => {
    if (paused) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }
    intervalRef.current = setInterval(() => void refresh(), REFRESH_INTERVAL_MS);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      intervalRef.current = null;
    };
  }, [paused, refresh]);

  const handleCancelSession = useCallback(async (sessionId: string) => {
    const ok = await api.cancelSession(sessionId);
    setToast({
      message: ok
        ? `Killed session ${sessionId.slice(0, 8)}…`
        : `Failed to kill session ${sessionId.slice(0, 8)}…`,
      severity: ok ? 'success' : 'error',
    });
    void refresh();
  }, [api, refresh]);

  const handleCancelVerification = useCallback(async (verificationId: string) => {
    const ok = await api.cancelVerification(verificationId);
    setToast({
      message: ok
        ? `Cancelled verification ${verificationId}`
        : `Failed to cancel verification ${verificationId}`,
      severity: ok ? 'success' : 'error',
    });
    void refresh();
  }, [api, refresh]);

  const filteredSessions = useMemo(() => {
    if (!data) return [];
    const needle = filter.trim().toLowerCase();
    if (!needle) return data.admin.sessions;
    return data.admin.sessions.filter((s) =>
      s.sessionId.toLowerCase().includes(needle) ||
      s.remoteIp.toLowerCase().includes(needle) ||
      s.flavorId.toLowerCase().includes(needle),
    );
  }, [data, filter]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100dvh', bgcolor: 'var(--page-bg)' }}>
        <AdminHeader
          lastRefreshAt={null}
          preference={preference}
          colorMode={colorMode}
          onToggleColorMode={onToggleColorMode}
        />
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1 }}>
          <CircularProgress sx={{ color: 'text.secondary' }} />
        </Box>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100dvh', bgcolor: 'var(--page-bg)' }}>
      <AdminHeader
        lastRefreshAt={lastRefreshAt}
        paused={paused}
        onTogglePause={() => { setPaused((p) => !p); }}
        onLogout={onLogout}
        preference={preference}
        colorMode={colorMode}
        onToggleColorMode={onToggleColorMode}
      />
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
        {error && <Alert severity="error" sx={{ mx: { xs: 1, sm: 3 }, mt: 2 }}>{error}</Alert>}

        {data && (
          <>
            <KpiStrip
              uptime={data.info.uptime}
              startedAt={data.info.startedAt}
              activeSessions={data.info.activeSessions}
              maxSessions={data.info.maxSessions}
              verificationConcurrency={data.config.verification.concurrency}
              sessions={data.admin.sessions}
            />

            <Box sx={{ px: { xs: 1, sm: 3 }, mt: 2, borderBottom: '1px solid var(--surface-border)' }}>
              <Tabs
                value={tab}
                onChange={(_, v: AdminTab) => { setTab(v); }}
                variant="scrollable"
                scrollButtons="auto"
                sx={{
                  minHeight: 36,
                  '& .MuiTab-root': {
                    minHeight: 36,
                    fontSize: FONT_SIZE.sm,
                    color: 'text.secondary',
                  },
                  '& .Mui-selected': { color: 'text.primary' },
                }}
              >
                <Tab value="sessions" label={`Sessions (${data.admin.sessions.length})`} />
                <Tab value="config" label="Config" />
              </Tabs>
            </Box>

            <Box sx={{ px: { xs: 1, sm: 3 }, py: 2 }}>
              {tab === 'sessions' && (
                <SessionsTab
                  sessions={filteredSessions}
                  totalSessions={data.admin.sessions.length}
                  filter={filter}
                  onFilterChange={setFilter}
                  onCancelSession={(id) => void handleCancelSession(id)}
                  onCancelVerification={(id) => void handleCancelVerification(id)}
                />
              )}
              {tab === 'config' && (
                <ConfigTab
                  info={data.info}
                  config={data.config}
                  frontendCommit={__GIT_COMMIT__}
                  frontendBuildTime={__BUILD_TIME__}
                  portfolios={portfolios}
                  flavors={flavors}
                  sessions={data.admin.sessions}
                />
              )}
            </Box>
          </>
        )}
      </Box>
      <Snackbar
        open={toast !== null}
        autoHideDuration={4000}
        onClose={() => { setToast(null); }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {toast ? (
          <Alert severity={toast.severity} onClose={() => { setToast(null); }} sx={{ width: '100%' }}>
            {toast.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </Box>
  );
}

interface AdminPageProps {
  colorModePreference: ColorModePreference;
  colorMode: ResolvedColorMode;
  onToggleColorMode: () => void;
}

export default function AdminPage({ colorModePreference, colorMode, onToggleColorMode }: AdminPageProps): React.JSX.Element {
  const api = useMemo(() => createApi(resolveBackendUrl()), []);

  const [authState, setAuthState] = useState<'checking' | 'unauthenticated' | 'authenticated'>('checking');
  const [authError, setAuthError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void api.checkAdminAuth()
      .then((ok) => {
        if (cancelled) return;
        setAuthState(ok ? 'authenticated' : 'unauthenticated');
      })
      .catch(() => {
        // Surface the login screen on network failure rather than spinning forever.
        if (cancelled) return;
        setAuthState('unauthenticated');
      });
    return () => {
      cancelled = true;
    };
  }, [api]);

  const handleLogin = useCallback(async (pw: string) => {
    try {
      const ok = await api.loginAdmin(pw);
      setAuthError(!ok);
      if (ok) {
        setAuthState('authenticated');
      }
    } catch {
      setAuthError(true);
    }
  }, [api]);

  const handleLogout = useCallback(async () => {
    try {
      await api.logoutAdmin();
    } catch {
      /* The user clearly wants to leave; flip local state regardless of network outcome. */
    }
    setAuthState('unauthenticated');
  }, [api]);

  if (authState === 'checking') {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100dvh', bgcolor: 'var(--page-bg)' }}>
        <CircularProgress sx={{ color: 'text.secondary' }} />
      </Box>
    );
  }

  if (authState === 'unauthenticated') {
    return <LoginForm onLogin={(pw) => void handleLogin(pw)} error={authError} />;
  }

  return (
    <Dashboard
      api={api}
      onLogout={() => void handleLogout()}
      preference={colorModePreference}
      colorMode={colorMode}
      onToggleColorMode={onToggleColorMode}
    />
  );
}
