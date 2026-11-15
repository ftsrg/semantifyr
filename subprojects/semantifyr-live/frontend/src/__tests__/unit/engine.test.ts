/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import type { ActiveVerificationInfo } from '../../lib/api/types';
import {
  applyServerReport,
  discoverCases,
  dispatchAutoValidation,
  markQueued,
  runAllVerifications,
  unmarkQueued,
  validateWitness,
  type AutoValidateRequest,
  type LspClient,
  type VerificationCaseState,
  type VerificationState,
} from '../../lib/verification/engine';
import type { VerificationCaseResult } from '@semantifyr/editor-common';
import { sampleCase } from '../fixtures/cases';
import {
  failedResult,
  passedResult,
  sampleMetrics,
  sampleTrace,
} from '../fixtures/verification-results';

function captureStates(): {
  states: VerificationState[];
  setState: (next: VerificationState | ((previous: VerificationState) => VerificationState)) => void;
} {
  const states: VerificationState[] = [];
  let current: VerificationState = { phase: 'idle', discovery: 'idle', cases: [] };
  const setState = (next: VerificationState | ((previous: VerificationState) => VerificationState)): void => {
    current = typeof next === 'function' ? next(current) : next;
    states.push(current);
  };
  return { states, setState };
}

const noopProgress = (_: (parameters: unknown) => void) => () => {};

describe('runAllVerifications', () => {
  it('sends every case at once and folds in each verdict with its metrics + backendId', async () => {
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
      { portfolioId: 'smart' },
    );

    await vi.waitFor(() => {
      expect(states.at(-1)?.phase).toBe('done');
    });

    const final = states.at(-1)!;
    expect(final.cases.map((verificationCase) => verificationCase.status)).toEqual(['passed', 'failed']);
    expect(final.cases[0]!.metrics?.totalDuration).toBe('PT1S');
    expect(final.cases[0]!.backendId).toBe('theta-cegar');
    expect(final.cases[0]!.portfolioId).toBe('smart');
    expect(final.cases[1]!.trace).toBeDefined();
    expect(final.message).toMatch(/not every case passed/i);

    expect(sendRequest.mock.calls[0]![1]).toEqual({
      command: 'oxsts.case.verify',
      arguments: [{ uri: 'file:///workspace/snippet.oxsts', range: sampleCase('a').location.range, portfolio: 'smart' }],
    });

    expect(states[0]!.cases.every((verificationCase) => verificationCase.status === 'running')).toBe(true);
    expect(sendRequest).toHaveBeenCalledTimes(2);
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
    const sendRequest = vi.fn<LspClient['sendRequest']>(async (_method, parameters) => {
      const command = (parameters as { command?: string }).command;
      if (command === 'oxsts.case.verify') {
        return failedResult;
      }
      // validate response
      return {
        status: 'valid',
        message: null,
        metrics: sampleMetrics,
        backendId: 'theta-cegar',
        portfolioId: 'smart',
      };
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    const getAutoValidateRequest = (): AutoValidateRequest => ({
      command: 'oxsts.case.validateWitness',
      portfolioId: 'smart',
    });

    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.oxsts',
      [sampleCase('a')],
      setState,
      noopProgress,
      { portfolioId: 'smart', getAutoValidateRequest },
    );

    await vi.waitFor(() => {
      const last = states.at(-1)!;
      expect(last.cases[0]!.witnessValidation).toBe('valid');
    });
    expect(sendRequest).toHaveBeenCalledTimes(2);
    expect(states.some((state) => state.cases[0]?.validating === true)).toBe(true);
    expect(states.at(-1)!.cases[0]!.validationBackendId).toBe('theta-cegar');
  });

  it('sends the case location instead of the witness location when the command validates by case', async () => {
    const gammaCase = { ...sampleCase('a'), location: { uri: 'file:///workspace/snippet.gamma', range: { start: { line: 4, character: 0 }, end: { line: 6, character: 1 } } } };
    const sendRequest = vi.fn<LspClient['sendRequest']>(async (_method, parameters) => {
      if ((parameters as { command?: string }).command === 'gamma.case.verify') {
        return failedResult;
      }
      return { status: 'valid', verdict: 'passed', message: null, backendId: null, portfolioId: 'smart', metrics: null };
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    runAllVerifications(
      client,
      'gamma.case.verify',
      'file:///workspace/snippet.gamma',
      [gammaCase],
      setState,
      noopProgress,
      { getAutoValidateRequest: () => ({ command: 'gamma.witness.validate', byCase: true }) },
    );

    await vi.waitFor(() => {
      expect(states.at(-1)!.cases[0]!.witnessValidation).toBe('valid');
    });
    const parameters = sendRequest.mock.calls[1]![1] as { command: string; arguments: { uri: string; range: unknown }[] };
    expect(parameters.command).toBe('gamma.witness.validate');
    expect(parameters.arguments[0]!.uri).toBe(gammaCase.location.uri);
    expect(parameters.arguments[0]!.range).toEqual(gammaCase.location.range);
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
    expect(states.some((state) => state.cases[0]?.validating === true)).toBe(false);
  });
});

describe('verification statuses', () => {
  it.each([
    ['timeout', 'timeout'],
    ['cancelled', 'cancelled'],
    ['inconclusive', 'inconclusive'],
    ['mystery', 'errored'],
  ] as const)('maps a %s result to the %s case status', async (wireStatus, caseStatus) => {
    const response = { ...passedResult, status: wireStatus, message: 'note' };
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => response);
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
    expect(states.at(-1)?.cases[0]?.status).toBe(caseStatus);
    expect(states.at(-1)?.cases[0]?.message).toBe('note');
  });

  it.each([
    'timeout',
    'cancelled',
  ] as const)('keeps a %s witness validation status', async (status) => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => ({ status, message: 'note' }));
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const outcome = await validateWitness(
      client,
      'oxsts.case.validateWitness',
      { uri: 'inmemory:///x', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } },
    );
    expect(outcome.status).toBe(status);
    expect(outcome.message).toBe('note');
  });
});

describe('discoverCases', () => {
  it('is discovering while the request is pending and discovered with the cases once it resolves', async () => {
    let resolveDiscovery: (cases: unknown) => void = () => {};
    const sendRequest = vi.fn<LspClient['sendRequest']>(() => new Promise((resolve) => {
      resolveDiscovery = resolve;
    }));
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    const discovery = discoverCases(client, 'oxsts.case.discover', 'file:///workspace/snippet.oxsts', setState);

    expect(states.at(-1)?.discovery).toBe('discovering');
    resolveDiscovery([sampleCase('a')]);
    await discovery;

    expect(states.at(-1)?.discovery).toBe('discovered');
    expect(states.at(-1)?.cases.map((caseState) => caseState.caseInfo.id)).toEqual(['a']);
    expect(states.at(-1)?.cases[0]?.status).toBe('stale');
  });

  it('is discovered with no cases when the model declares none', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => []);
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    await discoverCases(client, 'oxsts.case.discover', 'file:///workspace/snippet.oxsts', setState);

    expect(states.at(-1)?.discovery).toBe('discovered');
    expect(states.at(-1)?.cases).toEqual([]);
  });

  it('keeps the verdict of a case discovered again', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => [sampleCase('a')]);
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    setState({
      phase: 'done',
      discovery: 'discovered',
      cases: [{ caseInfo: sampleCase('a'), status: 'passed' }],
    });
    await discoverCases(client, 'oxsts.case.discover', 'file:///workspace/snippet.oxsts', setState);

    expect(states.at(-1)?.cases[0]?.status).toBe('passed');
  });

  it('returns to idle when the discovery request fails', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => {
      throw new Error('closed');
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    await discoverCases(client, 'oxsts.case.discover', 'file:///workspace/snippet.oxsts', setState);

    expect(states.at(-1)?.discovery).toBe('idle');
  });
});

describe('validateWitness', () => {
  it('parses a valid response into a structured outcome', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => ({
      status: 'invalid',
      message: 'spurious counterexample',
      backendId: 'theta-bmc',
      portfolioId: 'smart',
      metrics: sampleMetrics,
    }));
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const outcome = await validateWitness(
      client,
      'oxsts.case.validateWitness',
      { uri: 'inmemory:///x', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } },
      { portfolioId: 'smart' },
    );
    expect(outcome.status).toBe('invalid');
    expect(outcome.backendId).toBe('theta-bmc');
    expect(outcome.portfolioId).toBe('smart');

    const callArguments = sendRequest.mock.calls[0]!;
    const parameters = callArguments[1] as { arguments: { portfolio?: string }[] };
    expect(parameters.arguments[0]).toEqual({ uri: 'inmemory:///x', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } }, portfolio: 'smart' });
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
      portfolioId: 'smart',
      metrics: sampleMetrics,
    }));
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    setState({
      phase: 'done',
      discovery: 'discovered',
      cases: [{ caseInfo: sampleCase('a'), status: 'failed', trace: sampleTrace }],
    });

    dispatchAutoValidation(
      client,
      'oxsts.case.validateWitness',
      'a',
      { uri: 'inmemory:///workspace/snippet.witness.oxsts', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } },
      setState,
      { validationPortfolioId: 'smart' },
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
      expect(last.cases[0]!.validationPortfolioIdUsed).toBe('smart');
    });
  });
});

describe('the server\'s report of the session\'s verifications', () => {
  const caseOnLine = (id: string, line: number): VerificationCaseState['caseInfo'] => {
    return {
      id,
      label: `Case ${id}`,
      location: { uri: 'file:///workspace/snippet.oxsts', range: { start: { line, character: 2 }, end: { line, character: 9 } } },
    };
  };
  const entry = (kind: ActiveVerificationInfo['kind'], state: ActiveVerificationInfo['state'], location: VerificationCaseState['caseInfo']['location']): ActiveVerificationInfo => {
    return { verificationId: `${kind}-${state}`, portfolioId: 'smart', kind, state, elapsed: 'PT0S', location };
  };
  const sent: VerificationState = {
    phase: 'running',
    discovery: 'discovered',
    cases: [
      { caseInfo: caseOnLine('a', 1), status: 'running' },
      { caseInfo: caseOnLine('b', 2), status: 'queued' },
    ],
  };

  it('gives a sent case the state the server reports at its location, and the time it was first reported running', () => {
    const queued = applyServerReport(sent, [entry('Verify', 'Queued', caseOnLine('a', 1).location)], 100);
    expect(queued.cases[0]).toMatchObject({ serverState: 'Queued' });
    expect(queued.cases[0]?.runningSince).toBeUndefined();

    const running = applyServerReport(queued, [entry('Verify', 'Running', caseOnLine('a', 1).location)], 400);
    const again = applyServerReport(running, [entry('Verify', 'Running', caseOnLine('a', 1).location)], 900);
    expect(again.cases[0]).toMatchObject({ serverState: 'Running', runningSince: 400 });
    expect(again).toBe(running);
  });

  it('keeps what was last reported of a case the report leaves out, and ignores a case not sent and another kind', () => {
    const running = applyServerReport(sent, [entry('Verify', 'Running', caseOnLine('a', 1).location)], 400);
    expect(applyServerReport(running, [], 500)).toBe(running);
    expect(applyServerReport(sent, [
      entry('Verify', 'Queued', caseOnLine('b', 2).location),
      entry('Validate', 'Queued', caseOnLine('a', 1).location),
    ], 500)).toBe(sent);
  });

  it('gives a validation the state the server reports for its witness', () => {
    const validating: VerificationState = {
      phase: 'done',
      discovery: 'discovered',
      cases: [{ caseInfo: caseOnLine('a', 1), status: 'failed', validating: true, trace: sampleTrace }],
    };
    const witness = { uri: sampleTrace.witnessUri!, range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } };
    expect(applyServerReport(validating, [entry('Validate', 'Queued', witness)], 0).cases[0]).toMatchObject({ validationServerState: 'Queued' });
  });
});

describe('cases asked for before they can be sent', () => {
  const asked: VerificationState = {
    phase: 'idle',
    discovery: 'discovered',
    cases: [
      { caseInfo: sampleCase('a'), status: 'passed', resultStatus: 'passed' },
      { caseInfo: sampleCase('b'), status: 'stale' },
    ],
  };

  it('are queued, one or all, and on cancel return to their verdict or to not run', () => {
    expect(markQueued(asked, 'b', 0).cases.map((caseState) => {
      return caseState.status;
    })).toEqual(['passed', 'queued']);
    const all = markQueued(asked, null, 0);
    expect(all.cases.map((caseState) => {
      return caseState.status;
    })).toEqual(['queued', 'queued']);
    expect(unmarkQueued(all).cases.map((caseState) => {
      return [caseState.status, caseState.message];
    })).toEqual([['passed', undefined], ['stale', 'Verification cancelled']]);
  });
});

describe('the wait of a case', () => {
  const caseInfo = { ...sampleCase('a'), location: { uri: 'file:///workspace/snippet.oxsts', range: { start: { line: 3, character: 0 }, end: { line: 3, character: 1 } } } };
  const running = (now: number) => {
    return (state: VerificationState): VerificationState => {
      return applyServerReport(state, [{ verificationId: 'v', portfolioId: 'smart', kind: 'Verify', state: 'Running', elapsed: 'PT0S', location: caseInfo.location }], now);
    };
  };

  it.each([
    ['sent at once', null, 4_000],
    ['held for the language server', 1_000, 6_000],
  ] as const)('runs from when it was asked for to the server running it, for a case %s', async (_name, heldAt, waited) => {
    const clock = vi.spyOn(Date, 'now').mockReturnValue(3_000);
    let answer: (result: VerificationCaseResult) => void = () => {};
    const sendRequest = vi.fn<LspClient['sendRequest']>(() => new Promise((resolve) => {
      answer = resolve;
    }));
    const { states, setState } = captureStates();
    if (heldAt !== null) {
      setState(markQueued({ phase: 'idle', discovery: 'discovered', cases: [{ caseInfo, status: 'stale' }] }, null, heldAt));
    }
    runAllVerifications({ sendRequest, sendNotification: () => {} }, 'oxsts.case.verify', caseInfo.location.uri, [caseInfo], setState, noopProgress);
    await vi.waitFor(() => {
      expect(sendRequest).toHaveBeenCalled();
    });
    setState(running(7_000));
    answer(passedResult);

    await vi.waitFor(() => {
      expect(states.at(-1)?.phase).toBe('done');
    });
    expect(states.at(-1)?.cases[0]?.waited).toBe(waited);
    clock.mockRestore();
  });
});
