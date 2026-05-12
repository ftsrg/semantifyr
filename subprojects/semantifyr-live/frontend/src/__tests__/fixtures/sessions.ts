/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { SessionInfo } from '../../lib/api';

/**
 * Canonical {@link SessionInfo} fixture for tests. Tests that don't care about a particular
 * field rely on the defaults; pass {@code overrides} to pin the bits the assertion targets.
 */
export function fakeSession(overrides: Partial<SessionInfo> & { sessionId: string }): SessionInfo {
  return {
    remoteIp: '127.0.0.1',
    flavorId: 'oxsts',
    uptime: 'PT1M',
    workingDirectory: '/tmp/x',
    activeVerifications: [],
    started: true,
    bridgeInfo: {
      clientMessageCount: 0,
      serverMessageCount: 0,
      errorCount: 0,
      timeSinceLastClientMessage: 'PT0S',
      timeSinceLastServerMessage: 'PT0S',
    },
    ...overrides,
  };
}
