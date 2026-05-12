/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import VerificationPanel from '../components/verification/VerificationPanel';
import type { LiveEditorHandle } from '../components/editor/LiveEditor';
import type { VerificationCaseSpecification } from '@semantifyr/editor-common';
import type { LspClient, VerificationCaseState } from '../lib/verification';
import { sampleCase } from './fixtures/cases';
import { failedResult, passedResult } from './fixtures/verification-results';

interface FakeHandle {
  handle: LiveEditorHandle;
  resolveDiscovery: (cases: VerificationCaseSpecification[]) => void;
  sendRequest: ReturnType<typeof vi.fn>;
}

function createFakeHandle(): FakeHandle {
  let resolveDiscovery: (cases: VerificationCaseSpecification[]) => void = () => {};
  // discoverVerificationCases (in @semantifyr/editor-common) sends workspace/executeCommand
  // with the discovery command in params.command; verifyCase does the same with the verify
  // command. Route on params.command so a single fake covers both flows.
  const sendRequestRouted = vi.fn<LspClient['sendRequest']>(async (method, params) => {
    if (method !== 'workspace/executeCommand') {
      return null;
    }
    const cmd = (params as { command?: string }).command;
    if (cmd === 'oxsts.case.discover') {
      return new Promise((resolve) => {
        resolveDiscovery = (cs) => resolve(cs);
      });
    }
    if (cmd === 'oxsts.case.verify') {
      return passedResult;
    }
    return null;
  });

  const handle: LiveEditorHandle = {
    reconnect: () => {},
    disconnect: () => {},
    goToCase: () => {},
    getLspClient: () => ({ sendRequest: sendRequestRouted, sendNotification: () => {} }),
    getLspMetrics: () => null,
    getApi: () => null,
    getFileUri: () => 'inmemory:///snippet.oxsts',
    getCurrentCode: () => '',
    onEditorContentChange: () => () => {},
    addProgressListener: () => () => {},
    addNotificationListener: () => () => {},
    setVerifyCaseMarkers: () => {},
    getProblems: () => [],
    addProblemsListener: () => () => {},
    revealProblem: () => {},
    attachReadonlyEditor: async () => ({ dispose: () => {} }),
  };
  return {
    handle,
    resolveDiscovery: (cs) => resolveDiscovery(cs),
    sendRequest: sendRequestRouted,
  };
}


function renderPanel(args: {
  fake: FakeHandle;
  validateWitnessCommand?: string;
  onCasesChange?: (cases: readonly VerificationCaseState[]) => void;
  onShowWitness?: (caseId: string) => void;
  onAutoValidateChange?: (enabled: boolean) => void;
  autoValidate?: boolean;
}) {
  const onStatusMessage = vi.fn();
  // exactOptionalPropertyTypes forbids passing `undefined` to optional props, so spread the
  // optional callbacks conditionally instead of writing `prop={maybeUndef}` literally.
  const optional: Partial<{
    validateWitnessCommand: string;
    onCasesChange: (cases: readonly VerificationCaseState[]) => void;
    onShowWitness: (caseId: string) => void;
    onAutoValidateChange: (enabled: boolean) => void;
  }> = {};
  if (args.validateWitnessCommand !== undefined) optional.validateWitnessCommand = args.validateWitnessCommand;
  if (args.onCasesChange !== undefined) optional.onCasesChange = args.onCasesChange;
  if (args.onShowWitness !== undefined) optional.onShowWitness = args.onShowWitness;
  if (args.onAutoValidateChange !== undefined) optional.onAutoValidateChange = args.onAutoValidateChange;
  return {
    onStatusMessage,
    ...render(
      <VerificationPanel
        editorHandle={args.fake.handle}
        verificationCommand="oxsts.case.verify"
        discoveryCommand="oxsts.case.discover"
        connected
        portfolioId="smart-full"
        validationPortfolioId="smart-full"
        autoValidate={args.autoValidate ?? false}
        panelHeight={240}
        onClose={() => {}}
        onStatusMessage={onStatusMessage}
        {...optional}
      />,
    ),
  };
}

describe('VerificationPanel', () => {
  it('discovers cases on mount and renders one row per case', async () => {
    const fake = createFakeHandle();
    renderPanel({ fake });

    // The panel kicks off discovery on mount. Resolve with two cases.
    fake.resolveDiscovery([sampleCase('a'), sampleCase('b')]);

    expect(await screen.findByText('Case a')).toBeInTheDocument();
    expect(screen.getByText('Case b')).toBeInTheDocument();
  });

  it('reports status messages: "Discovering..." (idle/null) -> running -> verdict', async () => {
    const fake = createFakeHandle();
    const { onStatusMessage } = renderPanel({ fake });

    fake.resolveDiscovery([sampleCase('a')]);
    await screen.findByText('Case a');

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Run verification' }));

    await waitFor(() => {
      const calls = onStatusMessage.mock.calls;
      // Final call is the terminal verdict (busy=false, message describing all-passed).
      const last = calls[calls.length - 1]!;
      expect(last[1]).toBe(false);
      expect(last[0]).toMatch(/all cases passed/i);
    });
    // The intermediate "Verifying ..." status was reported with busy=true.
    expect(onStatusMessage.mock.calls.some(([msg, busy]) =>
      typeof msg === 'string' && /Verifying Case a/i.test(msg) && busy === true,
    )).toBe(true);
  });

  it('cancel button collapses queued/running cases back to stale and ends in cancelled phase', async () => {
    const fake = createFakeHandle();
    // Make verify never resolve so we can cancel mid-flight.
    fake.sendRequest.mockImplementation(async (method, params) => {
      if (method !== 'workspace/executeCommand') return null;
      const cmd = (params as { command?: string }).command;
      if (cmd === 'oxsts.case.discover') {
        return [sampleCase('a'), sampleCase('b')];
      }
      if (cmd === 'oxsts.case.verify') {
        return new Promise(() => { /* hangs */ });
      }
      return null;
    });

    const { onStatusMessage } = renderPanel({ fake });
    await screen.findByText('Case a');

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Run verification' }));
    // Wait for the running state to surface.
    await waitFor(() => {
      expect(onStatusMessage.mock.calls.some(([_msg, busy]) => busy === true)).toBe(true);
    });

    await user.click(screen.getByRole('button', { name: 'Cancel verification' }));
    await waitFor(() => {
      const last = onStatusMessage.mock.calls.at(-1)!;
      expect(last[1]).toBe(false);
      expect(last[0]).toMatch(/cancelled/i);
    });
  });

  it('renders the auto-validate switch only when validateWitnessCommand is supplied', async () => {
    const fakeWith = createFakeHandle();
    const { unmount } = renderPanel({
      fake: fakeWith,
      validateWitnessCommand: 'oxsts.case.validateWitness',
      onAutoValidateChange: () => {},
    });
    fakeWith.resolveDiscovery([sampleCase('a')]);
    await screen.findByText('Case a');
    expect(screen.getByText('auto-validate')).toBeInTheDocument();
    unmount();

    const fakeWithout = createFakeHandle();
    renderPanel({ fake: fakeWithout });
    fakeWithout.resolveDiscovery([sampleCase('a')]);
    await screen.findByText('Case a');
    expect(screen.queryByText('auto-validate')).not.toBeInTheDocument();
  });

  it('forwards the user-flipped auto-validate value via onAutoValidateChange', async () => {
    const fake = createFakeHandle();
    const onAutoValidateChange = vi.fn();
    renderPanel({
      fake,
      validateWitnessCommand: 'oxsts.case.validateWitness',
      autoValidate: false,
      onAutoValidateChange,
    });
    fake.resolveDiscovery([sampleCase('a')]);
    await screen.findByText('Case a');

    const user = userEvent.setup();
    await user.click(screen.getByRole('switch'));
    expect(onAutoValidateChange).toHaveBeenCalledWith(true);
  });

  it('show-witness button surfaces only when the case has a trace and forwards the case id', async () => {
    const fake = createFakeHandle();
    fake.sendRequest.mockImplementation(async (method, params) => {
      if (method !== 'workspace/executeCommand') return null;
      const cmd = (params as { command?: string }).command;
      if (cmd === 'oxsts.case.discover') return [sampleCase('a')];
      if (cmd === 'oxsts.case.verify') return failedResult;
      return null;
    });

    const onShowWitness = vi.fn();
    renderPanel({ fake, onShowWitness });
    await screen.findByText('Case a');

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Run verification' }));

    const witnessButton = await screen.findByRole('button', { name: 'Show witness' });
    await user.click(witnessButton);
    expect(onShowWitness).toHaveBeenCalledWith('a');
  });
});
