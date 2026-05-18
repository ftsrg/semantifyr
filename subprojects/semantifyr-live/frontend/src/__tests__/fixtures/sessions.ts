/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { SessionInfo } from '../../lib/api';

export function fakeSession(overrides: Partial<SessionInfo> & { sessionId: string }): SessionInfo {
  return {
    remoteIp: '127.0.0.1',
    flavorId: 'oxsts',
    uptime: 'PT1M',
    workingDirectory: '/tmp/x',
    activeVerifications: [],
    ...overrides,
  };
}
