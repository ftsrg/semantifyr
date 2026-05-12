/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import {
  dispatchAutoValidation,
  runAllVerifications,
  validateWitness,
  type AutoValidateRequest,
  type LspClient,
  type VerificationState,
} from '../lib/verification';
import type { VerificationCaseResult } from '@semantifyr/editor-common';
import { sampleCase } from './fixtures/cases';
import {
  failedResult,
  passedResult,
  sampleMetrics,
  sampleTrace,
} from './fixtures/verification-results';

function captureStates(): {
  states: VerificationState[];
  setState: (next: VerificationState | ((prev: VerificationState) => VerificationState)) => void;
} {
  const states: VerificationState[] = [];
  let current: VerificationState = { phase: 'idle', cases: [] };
  const setState = (next: VerificationState | ((prev: VerificationState) => VerificationState)): void => {
    current = typeof next === 'function' ? next(current) : next;
    states.push(current);
  };
  return { states, setState };
}

const noopProgress = (_: (params: unknown) => void) => () => {};

describe('runAllVerifications', () => {
  it('walks queued -> running -> verdict for each case and folds in metrics + backendId', async () => {
    const responses = [passedResult, failedResult];
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => responses.shift() ?? null);
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.oxsts',
      [sampleCase('a'), sampleCase('b')],
      setState,
      noopProgress,
      { portfolioId: 'smart-full' },
    );

    await vi.waitFor(() => {
      expect(states.at(-1)?.phase).toBe('done');
    });

    const final = states.at(-1)!;
    expect(final.cases.map((c) => c.status)).toEqual(['passed', 'failed']);
    expect(final.cases[0]!.metrics?.totalDuration).toBe('PT1S');
    expect(final.cases[0]!.backendId).toBe('theta-cegar');
    expect(final.cases[0]!.portfolioId).toBe('smart-full');
    expect(final.cases[1]!.trace).toBeDefined();
    expect(final.message).toMatch(/some cases failed/i);

    // The first batch transition should mark all cases queued.
    expect(states[0]!.cases.every((c) => c.status === 'queued')).toBe(true);
  });

  it('reports "All cases passed" when every verdict is passed', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => passedResult);
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.oxsts',
      [sampleCase('a')],
      setState,
      noopProgress,
    );

    await vi.waitFor(() => {
      expect(states.at(-1)?.phase).toBe('done');
    });
    expect(states.at(-1)!.message).toMatch(/all cases passed/i);
  });

  it('marks the case errored with the thrown message when sendRequest rejects', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => {
      throw new Error('LSP exploded');
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.oxsts',
      [sampleCase('a')],
      setState,
      noopProgress,
    );

    await vi.waitFor(() => {
      expect(states.at(-1)?.phase).toBe('done');
    });
    const final = states.at(-1)!;
    expect(final.cases[0]!.status).toBe('errored');
    expect(final.cases[0]!.message).toContain('LSP exploded');
  });

  it('dispatches a follow-up validateWitness when getAutoValidateRequest returns a command', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async (_method, params) => {
      const cmd = (params as { command?: string }).command;
      if (cmd === 'oxsts.case.verify') {
        return failedResult;
      }
      // validate response
      return {
        status: 'valid',
        message: null,
        metrics: sampleMetrics,
        backendId: 'theta-cegar',
        portfolioId: 'smart-full',
      };
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    const getAutoValidateRequest = (): AutoValidateRequest => ({
      command: 'oxsts.case.validateWitness',
      portfolioId: 'smart-full',
    });

    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.oxsts',
      [sampleCase('a')],
      setState,
      noopProgress,
      { portfolioId: 'smart-full', getAutoValidateRequest },
    );

    await vi.waitFor(() => {
      const last = states.at(-1)!;
      expect(last.cases[0]!.witnessValidation).toBe('valid');
    });
    expect(sendRequest).toHaveBeenCalledTimes(2);
    expect(states.some((s) => s.cases[0]?.validating === true)).toBe(true);
    expect(states.at(-1)!.cases[0]!.validationBackendId).toBe('theta-cegar');
  });

  it('skips auto-validation when the trace has no witnessUri', async () => {
    const noWitnessFail: VerificationCaseResult = { ...failedResult, trace: { ...sampleTrace, witnessUri: null } };
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => noWitnessFail);
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    const getAutoValidateRequest = (): AutoValidateRequest => ({ command: 'oxsts.case.validateWitness' });

    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.oxsts',
      [sampleCase('a')],
      setState,
      noopProgress,
      { getAutoValidateRequest },
    );

    await vi.waitFor(() => {
      expect(states.at(-1)?.phase).toBe('done');
    });
    expect(sendRequest).toHaveBeenCalledTimes(1);
    expect(states.some((s) => s.cases[0]?.validating === true)).toBe(false);
  });
});

describe('validateWitness', () => {
  it('parses a valid response into a structured outcome', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => ({
      status: 'invalid',
      message: 'spurious counterexample',
      backendId: 'theta-bmc',
      portfolioId: 'smart-full',
      metrics: sampleMetrics,
    }));
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const outcome = await validateWitness(
      client,
      'oxsts.case.validateWitness',
      { uri: 'inmemory:///x', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } },
      { portfolioId: 'smart-full', caseLabel: 'Case a' },
    );
    expect(outcome.status).toBe('invalid');
    expect(outcome.backendId).toBe('theta-bmc');
    expect(outcome.portfolioId).toBe('smart-full');

    const callArgs = sendRequest.mock.calls[0]!;
    const params = callArgs[1] as { arguments: Array<{ portfolio?: string; caseLabel?: string }> };
    expect(params.arguments[0]!.portfolio).toBe('smart-full');
    expect(params.arguments[0]!.caseLabel).toBe('Case a');
  });

  it('treats unknown response shapes as errored', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => ({ status: 'mystery' }));
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const outcome = await validateWitness(
      client,
      'oxsts.case.validateWitness',
      { uri: 'inmemory:///x', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } },
    );
    expect(outcome.status).toBe('errored');
  });

  it('collapses a thrown LSP error into an errored outcome with the message', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => { throw new Error('boom'); });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const outcome = await validateWitness(
      client,
      'oxsts.case.validateWitness',
      { uri: 'inmemory:///x', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } },
    );
    expect(outcome.status).toBe('errored');
    expect(outcome.message).toBe('boom');
  });
});

describe('dispatchAutoValidation', () => {
  it('flips validating=true synchronously and folds the validation result back in', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => ({
      status: 'valid',
      backendId: 'theta-cegar',
      portfolioId: 'smart-full',
      metrics: sampleMetrics,
    }));
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    setState({
      phase: 'done',
      cases: [{ caseInfo: sampleCase('a'), status: 'failed', trace: sampleTrace }],
    });

    dispatchAutoValidation(
      client,
      'oxsts.case.validateWitness',
      'a',
      'inmemory:///workspace/snippet.witness.oxsts',
      setState,
      { validationPortfolioId: 'smart-full', caseLabel: 'Case a' },
    );

    // First synchronous transition flips validating=true; the second (after the resolved
    // promise) folds in the outcome.
    const intermediate = states.at(-1)!;
    expect(intermediate.cases[0]!.validating).toBe(true);

    await vi.waitFor(() => {
      const last = states.at(-1)!;
      expect(last.cases[0]!.witnessValidation).toBe('valid');
      expect(last.cases[0]!.validating).toBe(false);
      expect(last.cases[0]!.validationBackendId).toBe('theta-cegar');
      expect(last.cases[0]!.validationPortfolioIdUsed).toBe('smart-full');
    });
  });
});
