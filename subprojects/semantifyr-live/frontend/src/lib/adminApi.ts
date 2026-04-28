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

/** Session info returned by both /api/admin/status and the semantifyr.session.info WebSocket command. */
export interface SessionInfo {
  sessionId: string;
  remoteIp: string;
  flavorId: string;
  uptime: string;
  workingDirectory: string;
  activeVerifications: string[];
  started: boolean;
  clientMessageCount: number;
  serverMessageCount: number;
  errorCount: number;
  timeSinceLastClientMessage: string;
  timeSinceLastServerMessage: string;
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

function authHeader(username: string, password: string): string {
  return 'Basic ' + btoa(`${username}:${password}`);
}

export async function fetchInfo(): Promise<InfoResponse> {
  const response = await fetch('/api/info');
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json() as Promise<InfoResponse>;
}

export async function cancelSession(password: string, sessionId: string): Promise<boolean> {
  const response = await fetch(`/api/admin/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
    headers: { Authorization: authHeader('admin', password) },
  });
  return response.ok;
}

export async function cancelVerification(password: string, sessionId: string, requestId: string): Promise<boolean> {
  const response = await fetch(`/api/admin/sessions/${encodeURIComponent(sessionId)}/verifications/${encodeURIComponent(requestId)}`, {
    method: 'DELETE',
    headers: { Authorization: authHeader('admin', password) },
  });
  return response.ok;
}

export async function fetchAdminConfig(password: string): Promise<AdminConfigResponse> {
  const response = await fetch('/api/admin/config', {
    headers: { Authorization: authHeader('admin', password) },
  });
  if (response.status === 401) throw new Error('Invalid password');
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json() as Promise<AdminConfigResponse>;
}

export async function fetchAdminStatus(password: string): Promise<AdminStatusResponse> {
  const response = await fetch('/api/admin/status', {
    headers: { Authorization: authHeader('admin', password) },
  });
  if (response.status === 401) throw new Error('Invalid password');
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json() as Promise<AdminStatusResponse>;
}
