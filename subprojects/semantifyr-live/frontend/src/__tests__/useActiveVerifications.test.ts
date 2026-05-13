/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { useActiveVerifications } from '../lib/hooks/useActiveVerifications';
import type { ActiveVerificationInfo } from '../lib/api/types';
import type { SemantifyrLiveApi } from '../lib/api/lspExtensions';

interface FakeApi {
  api: SemantifyrLiveApi;
  emit: (items: ActiveVerificationInfo[]) => void;
  unsubscribed: () => boolean;
  listVerifications: ReturnType<typeof vi.fn>;
  cancelVerification: ReturnType<typeof vi.fn>;
  cancelAllVerifications: ReturnType<typeof vi.fn>;
}

function fakeVerification(overrides: Partial<ActiveVerificationInfo>): ActiveVerificationInfo {
  return {
    verificationId: 'r1',
    portfolioId: 'smart-full',
    kind: 'Verify',
    elapsed: 'PT0S',
    ...overrides,
  };
}

function createFakeApi(initial: ActiveVerificationInfo[]): FakeApi {
  let handler: ((params: { active?: ActiveVerificationInfo[] }) => void) | null = null;
  let unsubscribed = false;
  const listVerifications = vi.fn(async () => ({ active: initial }));
  const cancelVerification = vi.fn(async () => true);
  const cancelAllVerifications = vi.fn(async () => 0);
  const api: Partial<SemantifyrLiveApi> = {
    listVerifications,
    cancelVerification,
    cancelAllVerifications,
    onVerificationsChanged: (cb: (params: { active?: ActiveVerificationInfo[] }) => void) => {
      handler = cb;
      return () => { unsubscribed = true; handler = null; };
    },
  };
  return {
    api: api as SemantifyrLiveApi,
    emit: (items) => { handler?.({ active: items }); },
    unsubscribed: () => unsubscribed,
    listVerifications,
    cancelVerification,
    cancelAllVerifications,
  };
}

describe('useActiveVerifications', () => {
  it('returns empty items and never calls the api when not connected', async () => {
    const fake = createFakeApi([fakeVerification({ verificationId: 'r1' })]);
    const { result } = renderHook(() => useActiveVerifications(fake.api, false));
    expect(result.current.items).toEqual([]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(fake.listVerifications).not.toHaveBeenCalled();
  });

  it('fetches on connect and updates on the changed notification', async () => {
    const fake = createFakeApi([fakeVerification({ verificationId: 'r1' })]);
    const { result } = renderHook(() => useActiveVerifications(fake.api, true));

    await waitFor(() => {
      expect(result.current.items.map((i) => i.verificationId)).toEqual(['r1']);
    });
    expect(fake.listVerifications).toHaveBeenCalled();

    act(() => {
      fake.emit([fakeVerification({ verificationId: 'r1' }), fakeVerification({ verificationId: 'r2', kind: 'Validate' })]);
    });
    expect(result.current.items.map((i) => i.verificationId)).toEqual(['r1', 'r2']);
  });

  it('treats a missing active field as empty', async () => {
    const fake = createFakeApi([]);
    fake.listVerifications.mockResolvedValueOnce({});
    const { result } = renderHook(() => useActiveVerifications(fake.api, true));
    await waitFor(() => {
      expect(result.current.items).toEqual([]);
    });
  });

  it('cancel + cancelAll forward to the api', async () => {
    const fake = createFakeApi([fakeVerification({ verificationId: 'r1' })]);
    const { result } = renderHook(() => useActiveVerifications(fake.api, true));
    await waitFor(() => { expect(result.current.items.length).toBe(1); });

    await act(async () => {
      await result.current.cancel('r1');
    });
    expect(fake.cancelVerification).toHaveBeenCalledWith('r1');

    await act(async () => {
      await result.current.cancelAll();
    });
    expect(fake.cancelAllVerifications).toHaveBeenCalled();
  });

  it('disposes the subscription on unmount', async () => {
    const fake = createFakeApi([]);
    const { unmount } = renderHook(() => useActiveVerifications(fake.api, true));
    await waitFor(() => { expect(fake.listVerifications).toHaveBeenCalled(); });
    unmount();
    expect(fake.unsubscribed()).toBe(true);
  });
});
