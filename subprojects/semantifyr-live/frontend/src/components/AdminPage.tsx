/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import AppBar from '@mui/material/AppBar';
import MuiToolbar from '@mui/material/Toolbar';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import { useColorMode } from '../lib/theme';
import ColorModeToggle from './ColorModeToggle';
import SessionList from './admin/SessionList';
import {
  fetchInfo, fetchAdminStatus, fetchAdminConfig,
  cancelSession, cancelVerification,
  type InfoResponse, type AdminStatusResponse, type AdminConfigResponse,
} from '../lib/adminApi';
import { formatIsoDuration } from '../lib/duration';

const cellSx = { color: 'var(--text)', fontSize: '0.85rem', borderColor: 'var(--surface-border)' } as const;
const labelSx = { ...cellSx, color: 'var(--text-muted)', fontWeight: 600 } as const;

function InfoTable({ rows }: { rows: [string, React.ReactNode][] }): React.JSX.Element {
  return (
    <Table size="small">
      <TableBody>
        {rows.map(([label, value]) => (
          <TableRow key={label}>
            <TableCell sx={labelSx}>{label}</TableCell>
            <TableCell sx={cellSx}>{value}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function AdminHeader(): React.JSX.Element {
  const { preference, colorMode, cycle } = useColorMode();
  const logoSrc = colorMode === 'dark' ? '/logo-full-dark.svg' : '/logo-full-light.svg';
  return (
    <AppBar position="static" elevation={0} sx={{ bgcolor: 'var(--surface-toolbar-bg)', borderBottom: '1px solid var(--surface-border)' }}>
      <MuiToolbar variant="dense" sx={{ px: 2, gap: 1, minHeight: 48 }}>
        <Box component="a" href="/" sx={{ display: 'inline-flex', alignItems: 'center', textDecoration: 'none' }}>
          <Box component="img" src={logoSrc} alt="Semantifyr" sx={{ height: '1.5rem', width: 'auto' }} />
        </Box>
        <Typography sx={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>/ Admin</Typography>
        <Box sx={{ flex: 1 }} />
        <ColorModeToggle preference={preference} onToggle={cycle} />
      </MuiToolbar>
    </AppBar>
  );
}

function LoginForm({ onLogin, error }: { onLogin: (password: string) => void; error: boolean }): React.JSX.Element {
  const [password, setPassword] = useState('');
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', bgcolor: 'var(--page-bg)', gap: 2 }}>
      <Typography variant="h5" sx={{ color: 'var(--text)', mb: 1 }}>Semantifyr Admin</Typography>
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
            '& .MuiInputBase-root': { color: 'var(--text)', bgcolor: 'var(--surface-bg)' },
            '& .MuiInputLabel-root': { color: 'var(--text-muted)' },
            '& .MuiOutlinedInput-notchedOutline': { borderColor: 'var(--surface-border)' },
          }}
        />
        <Button type="submit" variant="contained" sx={{ bgcolor: 'var(--accent)', '&:hover': { bgcolor: 'var(--accent-hover)' } }}>
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

const monoSpan = (value: string): React.ReactNode => (
  <Typography component="span" sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'var(--text)' }}>{value}</Typography>
);

function Dashboard({ password }: { password: string }): React.JSX.Element {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [info, admin, config] = await Promise.all([
        fetchInfo(),
        fetchAdminStatus(password),
        fetchAdminConfig(password),
      ]);
      setData({ info, admin, config });
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [password]);

  useEffect(() => {
    void refresh();
    intervalRef.current = setInterval(() => void refresh(), 1000);
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [refresh]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1, bgcolor: 'var(--page-bg)' }}>
        <CircularProgress sx={{ color: 'var(--text-muted)' }} />
      </Box>
    );
  }

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {error && <Alert severity="error" sx={{ mx: 3, mt: 2 }}>{error}</Alert>}

      {data && (
        <>
          <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)', mx: 3, mt: 2, mb: 0 }}>
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
              <Box sx={{ flex: 1, minWidth: 200 }}>
                <SectionTitle>Backend</SectionTitle>
                <InfoTable rows={[
                  ['Commit', monoSpan(data.info.commit)],
                  ['Built', data.info.buildTime],
                  ['Uptime', formatIsoDuration(data.info.uptime)],
                  ['Sessions', `${data.info.activeSessions} / ${data.info.maxSessions}`],
                ]} />
              </Box>
              <Box sx={{ flex: 1, minWidth: 200 }}>
                <SectionTitle>Frontend</SectionTitle>
                <InfoTable rows={[
                  ['Commit', monoSpan(__GIT_COMMIT__)],
                  ['Built', __BUILD_TIME__],
                ]} />
              </Box>
            </Box>
          </Paper>

          <Tabs
            value={tab}
            onChange={(_, v: number) => setTab(v)}
            sx={{
              mx: 3, mt: 2,
              '& .MuiTab-root': { color: 'var(--text-muted)', textTransform: 'none', fontSize: '0.9rem' },
              '& .Mui-selected': { color: 'var(--text)' },
              '& .MuiTabs-indicator': { bgcolor: 'var(--accent)' },
            }}
          >
            <Tab label={`Sessions (${data.info.activeSessions} / ${data.info.maxSessions})`} />
            <Tab label="Configuration" />
          </Tabs>

          <Box sx={{ flex: 1, overflow: 'auto', p: 3, maxWidth: 1100, mx: 'auto', width: '100%' }}>
            {tab === 0 && (
              <>
                <SessionList
                  sessions={data.admin.sessions}
                  onCancelSession={(id) => { void cancelSession(password, id).then(() => refresh()); }}
                  onCancelVerification={(sid, rid) => { void cancelVerification(password, sid, rid).then(() => refresh()); }}
                />
                <Typography sx={{ color: 'var(--text-muted)', fontSize: '0.78rem' }}>
                  Auto-refreshes every second
                </Typography>
              </>
            )}
            {tab === 1 && (
              <Paper sx={{ bgcolor: 'var(--surface-bg)', border: '1px solid var(--surface-border)' }}>
                <InfoTable rows={[
                  ['Max sessions (global)', String(data.config.maxSessionsGlobal)],
                  ['Max sessions (per IP)', String(data.config.maxSessionsPerIp)],
                  ['Verification concurrency', String(data.config.verificationConcurrency)],
                  ['Verification timeout', formatIsoDuration(data.config.verificationTimeout)],
                ]} />
              </Paper>
            )}
          </Box>
        </>
      )}
    </Box>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }): React.JSX.Element {
  return <Typography sx={{ color: 'var(--text-muted)', fontSize: '0.8rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', px: 1.5, pt: 1, pb: 0.5 }}>{children}</Typography>;
}

export default function AdminPage(): React.JSX.Element {
  const [password, setPassword] = useState<string | null>(() => {
    if (typeof sessionStorage !== 'undefined') {
      return sessionStorage.getItem('admin-password');
    }
    return null;
  });
  const [authError, setAuthError] = useState(false);

  const handleLogin = useCallback(async (pw: string) => {
    try {
      await fetchAdminStatus(pw);
      setPassword(pw);
      setAuthError(false);
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem('admin-password', pw);
      }
    } catch {
      setAuthError(true);
    }
  }, []);

  if (!password) {
    return <LoginForm onLogin={handleLogin} error={authError} />;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', bgcolor: 'var(--page-bg)' }}>
      <AdminHeader />
      <Dashboard password={password} />
    </Box>
  );
}
