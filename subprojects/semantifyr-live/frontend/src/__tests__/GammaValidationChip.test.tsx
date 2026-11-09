/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useRef, useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type {
  VerificationCaseResult,
  VerificationCaseSpecification,
  WitnessValidationResult,
} from '@semantifyr/editor-common';
import VerificationPanel, { type VerificationPanelHandle } from '../components/verification/VerificationPanel';
import WitnessTab from '../components/witness/WitnessTab';
import type { LiveEditorHandle } from '../components/editor/LiveEditor';
import type { LspClient, VerificationCaseState } from '../lib/verification';
import { passedResult, sampleTrace } from './fixtures/verification-results';

const gammaCase: VerificationCaseSpecification = {
  id: 'Crossroads.ACanBeGreen',
  label: 'ACanBeGreen',
  location: {
    uri: 'file:///workspace/snippet.gamma',
    range: { start: { line: 215, character: 0 }, end: { line: 217, character: 1 } },
  },
};

const verifiedWithTrace: VerificationCaseResult = {
  ...passedResult,
  trace: { ...sampleTrace, witnessUri: 'file:///workspace/.artifacts/Crossroads.ACanBeGreen/verification/model.ACanBeGreen/trace.gamma' },
};

function validationResult(status: WitnessValidationResult['status'], message: string): WitnessValidationResult {
  return {
    status,
    verdict: status === 'errored' ? null : 'passed',
    message,
    backendId: null,
    portfolioId: 'smart-full',
    metrics: null,
  };
}

function createGammaClient(): {
  sendRequest: ReturnType<typeof vi.fn<LspClient['sendRequest']>>;
  resolveValidation: (result: WitnessValidationResult) => void;
} {
  let resolveValidation: (result: WitnessValidationResult) => void = () => {};
  const sendRequest = vi.fn<LspClient['sendRequest']>(async (_method, parameters) => {
    const command = (parameters as { command?: string }).command;
    if (command === 'gamma.case.discover') {
      return [gammaCase];
    }
    if (command === 'gamma.case.verify') {
      return verifiedWithTrace;
    }
    if (command === 'gamma.witness.validate') {
      return new Promise((resolve) => {
        resolveValidation = resolve;
      });
    }
    return null;
  });
  return { sendRequest, resolveValidation: (result) => { resolveValidation(result); } };
}

function editorHandle(sendRequest: LspClient['sendRequest']): LiveEditorHandle {
  return {
    reconnect: () => {},
    disconnect: () => {},
    goToCase: () => {},
    getLspClient: () => ({ sendRequest, sendNotification: () => {} }),
    getLspMetrics: () => null,
    getApi: () => null,
    getFileUri: () => 'file:///workspace/snippet.gamma',
    getCurrentCode: () => '',
    getMappedOxsts: () => null,
    addMappingListener: () => () => {},
    onEditorContentChange: () => () => {},
    addProgressListener: () => () => {},
    addNotificationListener: () => () => {},
    setVerifyCaseMarkers: () => {},
    getProblems: () => [],
    addProblemsListener: () => () => {},
    revealProblem: () => {},
    attachReadonlyEditor: async () => ({ dispose: () => {} }),
  };
}

// wires the panel and the witness pane as EditorPage does for the Gamma flavor
function GammaWitnessPane({ handle }: { handle: LiveEditorHandle }): React.JSX.Element {
  const panel = useRef<VerificationPanelHandle>(null);
  const [cases, setCases] = useState<readonly VerificationCaseState[]>([]);
  const shown = cases.find((caseState) => caseState.trace !== undefined);
  return (
    <>
      <VerificationPanel
        ref={panel}
        editorHandle={handle}
        verificationCommand="gamma.case.verify"
        discoveryCommand="gamma.case.discover"
        validateWitnessCommand="gamma.witness.validate"
        validateWitnessByCase
        connected
        portfolioId="smart-full"
        validationPortfolioId="smart-full"
        autoValidate={false}
        panelHeight={240}
        onClose={() => {}}
        onStatusMessage={() => {}}
        onCasesChange={setCases}
      />
      {shown?.trace && (
        <WitnessTab
          caseInfo={shown.caseInfo}
          trace={shown.trace}
          validation={shown.witnessValidation}
          validationMessage={shown.validationMessage}
          validating={shown.validating ?? false}
          canRevalidate
          onRevalidate={() => { panel.current?.revalidate(shown.caseInfo.id); }}
          editorHandle={null}
          witnessLanguageId="gamma"
        />
      )}
    </>
  );
}

async function verifyAndValidate(client: ReturnType<typeof createGammaClient>): Promise<ReturnType<typeof userEvent.setup>> {
  render(<GammaWitnessPane handle={editorHandle(client.sendRequest)} />);
  const user = userEvent.setup({ delay: null });
  await screen.findByText('ACanBeGreen');
  await user.click(screen.getByRole('button', { name: 'Run verification' }));
  expect(await screen.findByText('Not validated')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Validate witness' }));
  expect(await screen.findByText('Validating...')).toBeInTheDocument();
  return user;
}

// the pointer leaves the validate button's tooltip open, so look for the chip's by its text
async function tooltipShowing(text: string): Promise<HTMLElement | null> {
  const element = await screen.findByText(text, { exact: false });
  return element.closest('[role="tooltip"]');
}

// the first render of the whole witness pane styles every MUI component it uses, seconds in jsdom
describe('Gamma validation chip', { timeout: 20_000 }, () => {
  it('validates the shown case through gamma.witness.validate and shows the result with its message', async () => {
    const client = createGammaClient();
    const user = await verifyAndValidate(client);

    const validateCall = client.sendRequest.mock.calls.find(([, parameters]) => (parameters as { command?: string }).command === 'gamma.witness.validate');
    expect(validateCall?.[1]).toEqual({
      command: 'gamma.witness.validate',
      arguments: [{ uri: gammaCase.location.uri, range: gammaCase.location.range, portfolio: 'smart-full' }],
    });

    client.resolveValidation(validationResult('valid', 'Theta reports the property holds'));
    await user.hover(await screen.findByText('Witness valid'));
    expect(await tooltipShowing('Theta reports the property holds')).toBeInTheDocument();
  });

  it.each([
    ['invalid', 'Witness invalid', 'The re-verification did not reach the target'],
    ['inconclusive', 'Inconclusive', 'Timed out after 300 s'],
    ['errored', 'Validation errored', 'Case Crossroads.ACanBeGreen has no witness yet, verify it first'],
  ] as const)('shows a %s validation as %s with its message on hover', async (status, label, message) => {
    const client = createGammaClient();
    const user = await verifyAndValidate(client);

    client.resolveValidation(validationResult(status, message));
    await user.hover(await screen.findByText(label));
    expect(await tooltipShowing(message)).toBeInTheDocument();
  });
});
