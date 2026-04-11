/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ConnectionStatus from '../components/ConnectionStatus';

describe('ConnectionStatus', () => {
  const noop = (): void => {};

  it('shows a spinner when initializing', () => {
    render(<ConnectionStatus status="initializing" onReconnect={noop} onDisconnect={noop} />);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows a spinner when reconnecting', () => {
    render(<ConnectionStatus status="reconnecting" onReconnect={noop} onDisconnect={noop} />);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows a cloud icon when connected', () => {
    render(<ConnectionStatus status="connected" onReconnect={noop} onDisconnect={noop} />);
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('calls onDisconnect when clicking the connected icon', async () => {
    const onDisconnect = vi.fn();
    render(<ConnectionStatus status="connected" onReconnect={noop} onDisconnect={onDisconnect} />);
    await userEvent.setup().click(screen.getByRole('button'));
    expect(onDisconnect).toHaveBeenCalledOnce();
  });

  it('calls onReconnect when clicking the errored icon', async () => {
    const onReconnect = vi.fn();
    render(<ConnectionStatus status="errored" onReconnect={onReconnect} onDisconnect={noop} />);
    await userEvent.setup().click(screen.getByRole('button'));
    expect(onReconnect).toHaveBeenCalledOnce();
  });

  it('calls onReconnect when clicking the disconnected icon', async () => {
    const onReconnect = vi.fn();
    render(<ConnectionStatus status="disconnected" onReconnect={onReconnect} onDisconnect={noop} />);
    await userEvent.setup().click(screen.getByRole('button'));
    expect(onReconnect).toHaveBeenCalledOnce();
  });
});
