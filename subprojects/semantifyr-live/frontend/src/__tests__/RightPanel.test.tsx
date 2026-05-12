/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RightPanel, { type RightPanelTab } from '../components/shell/RightPanel';

const mockTab = (id: string, label: string): RightPanelTab => ({
  id,
  label,
  content: <div data-testid={`content-${id}`}>{`content for ${label}`}</div>,
});

describe('RightPanel', () => {
  it('renders the panel shell with a close button even when no tabs are bound', () => {
    render(
      <RightPanel
        tabs={[]}
        activeTabId={null}
        onActiveTabChange={() => {}}
        onClose={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: 'Close right panel' })).toBeInTheDocument();
    expect(screen.getByText('No active tabs.')).toBeInTheDocument();
  });

  it('renders the supplied tabs and surfaces the active tab body', () => {
    render(
      <RightPanel
        tabs={[mockTab('runs', 'Verifications'), mockTab('witness', 'Witness')]}
        activeTabId="witness"
        onActiveTabChange={() => {}}
        onClose={() => {}}
      />,
    );
    expect(screen.getByRole('tab', { name: 'Verifications' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Witness' })).toBeInTheDocument();
    expect(screen.getByTestId('content-witness')).toBeInTheDocument();
  });

  it('falls back to the first tab when the supplied activeTabId is missing from the list', () => {
    render(
      <RightPanel
        tabs={[mockTab('runs', 'Verifications'), mockTab('witness', 'Witness')]}
        activeTabId="does-not-exist"
        onActiveTabChange={() => {}}
        onClose={() => {}}
      />,
    );
    expect(screen.getByTestId('content-runs')).toBeInTheDocument();
  });

  it('forwards onActiveTabChange when the user clicks a tab', async () => {
    const onActiveTabChange = vi.fn();
    render(
      <RightPanel
        tabs={[mockTab('runs', 'Verifications'), mockTab('witness', 'Witness')]}
        activeTabId="runs"
        onActiveTabChange={onActiveTabChange}
        onClose={() => {}}
      />,
    );
    await userEvent.setup().click(screen.getByRole('tab', { name: 'Witness' }));
    expect(onActiveTabChange).toHaveBeenCalledWith('witness');
  });

  it('calls onClose when the panel close button is clicked', async () => {
    const onClose = vi.fn();
    render(
      <RightPanel
        tabs={[mockTab('runs', 'Verifications')]}
        activeTabId="runs"
        onActiveTabChange={() => {}}
        onClose={onClose}
      />,
    );
    await userEvent.setup().click(screen.getByRole('button', { name: 'Close right panel' }));
    expect(onClose).toHaveBeenCalledOnce();
  });
});
