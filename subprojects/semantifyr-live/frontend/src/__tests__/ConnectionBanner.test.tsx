/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ConnectionBanner from '../components/shell/ConnectionBanner';

function setNavigatorOnline(value: boolean): void {
  Object.defineProperty(window.navigator, 'onLine', { configurable: true, value });
}

describe('ConnectionBanner', () => {
  const noop = (): void => {};

  afterEach(() => {
    setNavigatorOnline(true);
  });

  it('renders nothing while the session is healthy or still coming up', () => {
    for (const status of ['connected', 'initializing', 'reconnecting'] as const) {
      const { container } = render(
        <ConnectionBanner status={status} statusInfo={null} onReconnect={noop} />,
      );
      expect(container).toBeEmptyDOMElement();
    }
  });

  it('shows the server-side failure copy when errored and online', () => {
    setNavigatorOnline(true);
    render(
      <ConnectionBanner status="errored" statusInfo="Failed to connect to LSP server" onReconnect={noop} />,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Connection lost')).toBeInTheDocument();
    expect(screen.getByText('Failed to connect to LSP server')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('leads with the offline framing when errored and the browser is offline', () => {
    setNavigatorOnline(false);
    render(
      <ConnectionBanner status="errored" statusInfo="Failed to connect to LSP server" onReconnect={noop} />,
    );
    expect(screen.getByText("You're offline")).toBeInTheDocument();
    expect(screen.queryByText('Failed to connect to LSP server')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('shows a quieter prompt for a user-initiated disconnect', () => {
    render(<ConnectionBanner status="disconnected" statusInfo={null} onReconnect={noop} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('Disconnected')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reconnect' })).toBeInTheDocument();
  });

  it('invokes onReconnect when the action button is clicked', async () => {
    const onReconnect = vi.fn();
    render(<ConnectionBanner status="errored" statusInfo={null} onReconnect={onReconnect} />);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Retry' }));
    expect(onReconnect).toHaveBeenCalledOnce();
  });

  it('can be dismissed and stays dismissed until the status changes again', async () => {
    const { rerender } = render(
      <ConnectionBanner status="errored" statusInfo="boom" onReconnect={noop} />,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    rerender(<ConnectionBanner status="errored" statusInfo="boom" onReconnect={noop} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    rerender(<ConnectionBanner status="reconnecting" statusInfo={null} onReconnect={noop} />);
    rerender(<ConnectionBanner status="errored" statusInfo="boom again" onReconnect={noop} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
});
