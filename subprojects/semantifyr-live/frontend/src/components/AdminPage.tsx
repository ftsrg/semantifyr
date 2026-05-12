/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Snackbar from '@mui/material/Snackbar';
import Tooltip from '@mui/material/Tooltip';
import PauseCircleOutlinedIcon from '@mui/icons-material/PauseCircleOutlined';
import PlayCircleOutlinedIcon from '@mui/icons-material/PlayCircleOutlined';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import LogoutIcon from '@mui/icons-material/Logout';
import type { ColorModePreference, ResolvedColorMode } from '../lib/util/colorMode';
import AppHeader from './shell/AppHeader';
import ColorModeToggle from './shell/ColorModeToggle';
import KpiStrip from './admin/KpiStrip';
import SessionsTable from './admin/SessionsTable';
import InfoAside from './admin/InfoAside';
import {
  createApi,
  type AdminConfigResponse,
  type AdminStatusResponse,
  type InfoResponse,
  type LiveServerApi,
  type PortfolioInfo,
} from '../lib/api';
import { resolveBackendUrl } from '../lib/util/backendUrl';
import { FONT_SIZE, ICON_SIZE } from '../lib/util/theme';

const REFRESH_INTERVAL_MS = 1000;

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
        <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}>
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
            {paused ? <PlayCircleOutlinedIcon sx={{ fontSize: ICON_SIZE.lg }} /> : <PauseCircleOutlinedIcon sx={{ fontSize: ICON_SIZE.lg }} />}
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
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100dvh', bgcolor: 'var(--page-bg)', gap: 2 }}>
      <Typography variant="h5" sx={{ mb: 1 }}>Semantifyr Admin</Typography>
      {error && <Alert severity="error">Invalid password</Alert>}
      <Box component="form" onSubmit={(e: React.FormEvent) => { e.preventDefault(); onLogin(password); }} sx={{ display: 'flex', gap: 1 }}>
        <TextField
          type="password"
          label="Admin password"
          size="small"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
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
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastRefreshAt, setLastRefreshAt] = useState<number | null>(null);
  const [paused, setPaused] = useState(false);
  const [filter, setFilter] = useState('');
  const [toast, setToast] = useState<ToastState | null>(null);
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
    void api.fetchPortfolios().then(setPortfolios).catch(() => setPortfolios([]));
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

  const handleCancelVerification = useCallback(async (sessionId: string, requestId: string) => {
    const ok = await api.cancelVerification(sessionId, requestId);
    setToast({
      message: ok
        ? `Cancelled verification ${requestId}`
        : `Failed to cancel verification ${requestId}`,
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
        onTogglePause={() => setPaused((p) => !p)}
        onLogout={onLogout}
        preference={preference}
        colorMode={colorMode}
        onToggleColorMode={onToggleColorMode}
      />
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
        {error && <Alert severity="error" sx={{ mx: 3, mt: 2 }}>{error}</Alert>}

        {data && (
          <>
            <KpiStrip
              uptime={data.info.uptime}
              activeSessions={data.info.activeSessions}
              maxSessions={data.info.maxSessions}
              sessions={data.admin.sessions}
            />

            <Box
              sx={{
                display: 'flex',
                flexDirection: { xs: 'column', md: 'row' },
                gap: 2,
                mx: 3,
                mt: 2,
                mb: 3,
                alignItems: 'flex-start',
              }}
            >
              <Box sx={{ flex: '1 1 auto', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
                <SessionsHeader
                  total={data.admin.sessions.length}
                  shown={filteredSessions.length}
                  filter={filter}
                  onFilterChange={setFilter}
                />
                <SessionsTable
                  sessions={filteredSessions}
                  onCancelSession={handleCancelSession}
                  onCancelVerification={handleCancelVerification}
                />
              </Box>
              <Box sx={{ flex: { xs: '1 1 auto', md: '0 0 320px' }, width: { xs: '100%', md: 320 } }}>
                <InfoAside
                  info={data.info}
                  config={data.config}
                  frontendCommit={__GIT_COMMIT__}
                  frontendBuildTime={__BUILD_TIME__}
                  portfolios={portfolios}
                />
              </Box>
            </Box>
          </>
        )}
      </Box>
      <Snackbar
        open={toast !== null}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {toast ? (
          <Alert severity={toast.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
            {toast.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </Box>
  );
}

interface SessionsHeaderProps {
  total: number;
  shown: number;
  filter: string;
  onFilterChange: (value: string) => void;
}

function SessionsHeader({ total, shown, filter, onFilterChange }: SessionsHeaderProps): React.JSX.Element {
  const filtered = filter.trim().length > 0;
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 0.5, flexWrap: 'wrap' }}>
      <Typography sx={{ fontSize: FONT_SIZE.lg, fontWeight: 600 }}>
        Sessions
      </Typography>
      <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>
        {total === 0
          ? 'none active'
          : filtered
            ? `${shown} of ${total} shown`
            : `${total} active`}
      </Typography>
      <Box sx={{ flex: 1, minWidth: 8 }} />
      <TextField
        size="small"
        placeholder="Filter by session, IP, or flavor"
        value={filter}
        onChange={(e) => onFilterChange(e.target.value)}
        sx={{
          width: { xs: '100%', sm: 280 },
          '& .MuiInputBase-root': { color: 'text.primary', bgcolor: 'var(--surface-bg)', fontSize: FONT_SIZE.sm },
          '& .MuiOutlinedInput-notchedOutline': { borderColor: 'var(--surface-border)' },
        }}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon sx={{ fontSize: ICON_SIZE.sm, color: 'text.secondary' }} />
              </InputAdornment>
            ),
            endAdornment: filter ? (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => onFilterChange('')} sx={{ color: 'text.secondary' }}>
                  <ClearIcon sx={{ fontSize: ICON_SIZE.sm }} />
                </IconButton>
              </InputAdornment>
            ) : undefined,
          },
        }}
      />
    </Box>
  );
}

interface AdminPageProps {
  colorModePreference: ColorModePreference;
  colorMode: ResolvedColorMode;
  onToggleColorMode: () => void;
}

export default function AdminPage({ colorModePreference, colorMode, onToggleColorMode }: AdminPageProps): React.JSX.Element {
  // One bound API client per AdminPage instance. resolveBackendUrl reads the same ?backend= /
  // VITE_BACKEND_URL the editor uses, so admin always speaks to the configured backend.
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
        // Network failures must NOT leave us spinning forever; surface the login screen so the
        // user can retry on their own terms (and see any login error reported by the backend
        // when the network comes back).
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
    return <LoginForm onLogin={handleLogin} error={authError} />;
  }

  return (
    <Dashboard
      api={api}
      onLogout={handleLogout}
      preference={colorModePreference}
      colorMode={colorMode}
      onToggleColorMode={onToggleColorMode}
    />
  );
}
