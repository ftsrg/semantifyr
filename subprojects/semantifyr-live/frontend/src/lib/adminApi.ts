/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface InfoResponse {
  uptime: string;
  commit: string;
  buildTime: string;
  activeSessions: number;
  maxSessions: number;
}

export type VerificationKind = 'Verify' | 'Validate';

export interface ActiveVerificationInfo {
  requestId: string;
  kind?: VerificationKind;
  caseLabel?: string | null;
  portfolioId?: string | null;
  /** ISO 8601 duration since the request was enqueued (e.g. "PT3.2S"). */
  elapsed?: string;
}

export interface LspProxyInfo {
  clientMessageCount: number;
  serverMessageCount: number;
  errorCount: number;
  timeSinceLastClientMessage: string;
  timeSinceLastServerMessage: string;
}

/** Session info returned by both /api/admin/status and the semantifyr.session.info WebSocket command. */
export interface SessionInfo {
  sessionId: string;
  remoteIp: string;
  flavorId: string;
  uptime: string;
  workingDirectory: string;
  activeVerifications: ActiveVerificationInfo[];
  started: boolean;
  bridgeInfo: LspProxyInfo;
}

export interface AdminStatusResponse {
  sessions: SessionInfo[];
}

export interface AdminConfigResponse {
  maxSessionsGlobal: number;
  maxSessionsPerIp: number;
  verificationConcurrency: number;
  verificationTimeout: string;
}

export async function fetchInfo(): Promise<InfoResponse> {
  const response = await fetch('/api/info');
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json() as Promise<InfoResponse>;
}

export async function loginAdmin(password: string): Promise<boolean> {
  const response = await fetch('/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  });
  return response.ok;
}

export async function logoutAdmin(): Promise<void> {
  await fetch('/api/admin/logout', { method: 'POST' });
}

export async function checkAdminAuth(): Promise<boolean> {
  const response = await fetch('/api/admin/whoami');
  return response.ok;
}

export async function cancelSession(sessionId: string): Promise<boolean> {
  const response = await fetch(`/api/admin/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  });
  return response.ok;
}

export async function cancelVerification(sessionId: string, requestId: string): Promise<boolean> {
  const response = await fetch(`/api/admin/sessions/${encodeURIComponent(sessionId)}/verifications/${encodeURIComponent(requestId)}`, {
    method: 'DELETE',
  });
  return response.ok;
}

export async function fetchAdminConfig(): Promise<AdminConfigResponse> {
  const response = await fetch('/api/admin/config');
  if (response.status === 401) throw new Error('Unauthorized');
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json() as Promise<AdminConfigResponse>;
}

export async function fetchAdminStatus(): Promise<AdminStatusResponse> {
  const response = await fetch('/api/admin/status');
  if (response.status === 401) throw new Error('Unauthorized');
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json() as Promise<AdminStatusResponse>;
}
