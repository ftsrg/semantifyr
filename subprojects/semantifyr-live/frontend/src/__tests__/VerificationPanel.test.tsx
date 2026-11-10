/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { FlavorInfo } from '@semantifyr/editor-common';
import VerificationPanel from '../components/verification/VerificationPanel';
import type { VerificationCaseSpecification } from '@semantifyr/editor-common';
import type { LspClient } from '../lib/verification';
import { LiveContext } from '../lib/workspace/liveContext';
import { sampleCase } from './fixtures/cases';
import { servedFlavors } from './fixtures/flavors';
import { failedResult, passedResult } from './fixtures/verification-results';
import { createTestLive, type TestLive } from './helpers/live';

vi.mock('vscode', () => {
  return {};
});
vi.mock('../lib/session/monaco', () => {
  return {};
});

interface FakeClient {
  client: LspClient;
  resolveDiscovery: (cases: VerificationCaseSpecification[]) => void;
  sendRequest: ReturnType<typeof vi.fn<LspClient['sendRequest']>>;
}

function createFakeClient(): FakeClient {
  let resolveDiscovery: (cases: VerificationCaseSpecification[]) => void = () => {};
  // discoverVerificationCases (in @semantifyr/editor-common) sends workspace/executeCommand
  // with the discovery command in params.command; verifyCase does the same with the verify
  // command. Route on params.command so a single fake covers both flows.
  const sendRequestRouted = vi.fn<LspClient['sendRequest']>(async (method, parameters) => {
    if (method !== 'workspace/executeCommand') {
      return null;
    }
    const command = (parameters as { command?: string }).command;
    if (command === 'oxsts.case.discover') {
      return new Promise((resolve) => {
        resolveDiscovery = (discovered) => { resolve(discovered); };
      });
    }
    if (command === 'oxsts.case.verify') {
      return passedResult;
    }
    return null;
  });
  return {
    client: { sendRequest: sendRequestRouted, sendNotification: () => {} },
    resolveDiscovery: (discovered) => { resolveDiscovery(discovered); },
    sendRequest: sendRequestRouted,
  };
}

async function renderPanel(options: {
  fake: FakeClient;
  served?: FlavorInfo[];
  onShowWitness?: (caseId: string) => void;
}): Promise<TestLive & ReturnType<typeof render>> {
  const test = createTestLive(options.fake.client, options.served ?? servedFlavors);
  test.live.start('');
  await waitFor(() => {
    expect(test.live.workspace.get().runtime).not.toBeNull();
  });
  const rendered = render(
    <LiveContext.Provider value={test.live}>
      <VerificationPanel
        panelHeight={240}
        onClose={() => {}}
        {...(options.onShowWitness ? { onShowWitness: options.onShowWitness } : {})}
      />
    </LiveContext.Provider>,
  );
  const uri = 'file:///workspace/snippet.oxsts';
  test.models.open(uri, 'model');
  act(() => {
    test.runtime().connect();
  });
  return { ...test, ...rendered };
}

function currentVerification(test: TestLive) {
  return test.live.verification.get().get(test.live.workspace.get().documentKey!)!;
}

describe('VerificationPanel', () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it('discovers cases once connected and renders one row per case', async () => {
    const fake = createFakeClient();
    await renderPanel({ fake });

    await waitFor(() => {
      expect(fake.sendRequest).toHaveBeenCalled();
    });
    fake.resolveDiscovery([sampleCase('a'), sampleCase('b')]);

    expect(await screen.findByText('Case a')).toBeInTheDocument();
    expect(screen.getByText('Case b')).toBeInTheDocument();
  });

  it('shows the discovering text only while discovery is pending', async () => {
    const fake = createFakeClient();
    await renderPanel({ fake });

    expect(await screen.findByText('Discovering cases...')).toBeInTheDocument();

    fake.resolveDiscovery([sampleCase('a')]);

    expect(await screen.findByText('Case a')).toBeInTheDocument();
    expect(screen.queryByText('Discovering cases...')).not.toBeInTheDocument();
  });

  it('says there are no verification cases after a discovery that finds none', async () => {
    const fake = createFakeClient();
    await renderPanel({ fake });

    await screen.findByText('Discovering cases...');
    fake.resolveDiscovery([]);

    expect(await screen.findByText('No verification cases')).toBeInTheDocument();
    expect(screen.queryByText('Discovering cases...')).not.toBeInTheDocument();
  });

  it('runs every case from the button and ends on the verdict message', async () => {
    const fake = createFakeClient();
    const test = await renderPanel({ fake });

    await screen.findByText('Discovering cases...');
    fake.resolveDiscovery([sampleCase('a')]);
    await screen.findByText('Case a');

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Run verification' }));

    await waitFor(() => {
      expect(currentVerification(test).phase).toBe('done');
    });
    expect(currentVerification(test).message).toMatch(/all cases passed/i);
  });

  it('cancel button ends the run in the cancelled phase', async () => {
    const fake = createFakeClient();
    // Make verify never resolve so we can cancel mid-flight.
    fake.sendRequest.mockImplementation(async (method, parameters) => {
      if (method !== 'workspace/executeCommand') return null;
      const command = (parameters as { command?: string }).command;
      if (command === 'oxsts.case.discover') {
        return [sampleCase('a'), sampleCase('b')];
      }
      if (command === 'oxsts.case.verify') {
        return new Promise(() => { /* hangs */ });
      }
      return null;
    });

    const test = await renderPanel({ fake });
    await screen.findByText('Case a');

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Run verification' }));
    await waitFor(() => {
      expect(currentVerification(test).phase).toBe('running');
    });

    await user.click(screen.getByRole('button', { name: 'Cancel verification' }));
    await waitFor(() => {
      expect(currentVerification(test).phase).toBe('cancelled');
    });
    expect(currentVerification(test).cases.map((caseState) => {
      return caseState.status;
    })).toEqual(['stale', 'stale']);
  });

  it('renders the auto-validate switch only when the flavor validates witnesses', async () => {
    const fakeWith = createFakeClient();
    const { unmount } = await renderPanel({ fake: fakeWith });
    await screen.findByText('Discovering cases...');
    fakeWith.resolveDiscovery([sampleCase('a')]);
    await screen.findByText('Case a');
    expect(screen.getByText('auto-validate')).toBeInTheDocument();
    unmount();

    const fakeWithout = createFakeClient();
    await renderPanel({ fake: fakeWithout, served: servedFlavors.map((flavor) => {
      return { ...flavor, validateWitnessCommand: null };
    }) });
    await screen.findByText('Discovering cases...');
    fakeWithout.resolveDiscovery([sampleCase('a')]);
    await screen.findByText('Case a');
    expect(screen.queryByText('auto-validate')).not.toBeInTheDocument();
  });

  it('remembers the flipped auto-validate switch in the settings', async () => {
    const fake = createFakeClient();
    const test = await renderPanel({ fake });
    await screen.findByText('Discovering cases...');
    fake.resolveDiscovery([sampleCase('a')]);
    await screen.findByText('Case a');
    expect(test.live.settings.get().autoValidate).toBe(true);

    const user = userEvent.setup();
    await user.click(screen.getByRole('switch'));

    expect(test.live.settings.get().autoValidate).toBe(false);
  });

  it('show-witness button surfaces only when the case has a trace and forwards the case id', async () => {
    const fake = createFakeClient();
    fake.sendRequest.mockImplementation(async (method, parameters) => {
      if (method !== 'workspace/executeCommand') return null;
      const command = (parameters as { command?: string }).command;
      if (command === 'oxsts.case.discover') return [sampleCase('a')];
      if (command === 'oxsts.case.verify') return failedResult;
      return null;
    });

    const onShowWitness = vi.fn();
    const test = await renderPanel({ fake, onShowWitness });
    await screen.findByText('Case a');
    test.live.settings.update((settings) => {
      return { ...settings, autoValidate: false };
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Run verification' }));

    const witnessButton = await screen.findByRole('button', { name: 'Show witness' });
    await user.click(witnessButton);
    expect(onShowWitness).toHaveBeenCalledWith('a');
  });
});
