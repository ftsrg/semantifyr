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
} from '../../lib/verification/runs';
import {
  applyServerReport,
  markQueued,
  type VerificationState,
} from '../../lib/verification/state';
import type { VerificationCaseResult } from '@semantifyr/editor-common';
import { sampleCase } from '../fixtures/cases';
import {
  failedResult,
  passedResult,
  sampleReport,
  sampleValidationReport,
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

const noopProgress = (_: (parameters: unknown) => void) => {
  return () => {};
};

describe('runAllVerifications', () => {
  it('sends every case at once and folds in each verdict with its report', async () => {
    const responses = [passedResult, failedResult];
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => {
      return responses.shift() ?? null;
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.semantifyr',
      [sampleCase('a'), sampleCase('b')],
      setState,
      noopProgress,
      { portfolioId: 'smart' },
    );

    await vi.waitFor(() => {
      expect(states.at(-1)?.phase).toBe('done');
    });

    const final = states.at(-1)!;
    expect(final.cases.map((verificationCase) => {
      return verificationCase.status;
    })).toEqual(['passed', 'failed']);
    expect(final.cases[0]!.report).toEqual(passedResult.report);
    expect(final.cases[0]!.portfolioId).toBe('smart');
    expect(final.cases[1]!.report).toEqual(failedResult.report);
    expect(final.cases[1]!.witnessUri).toBe(failedResult.witnessUri);
    expect(final.message).toMatch(/not every case passed/i);

    expect(sendRequest.mock.calls[0]![1]).toEqual({
      command: 'oxsts.case.verify',
      arguments: [{ uri: 'file:///workspace/snippet.semantifyr', range: sampleCase('a').location.range, portfolio: 'smart' }],
    });

    expect(states[0]!.cases.every((verificationCase) => {
      return verificationCase.status === 'running';
    })).toBe(true);
    expect(sendRequest).toHaveBeenCalledTimes(2);
  });

  it('reports "All cases passed" when every verdict is passed', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => {
      return passedResult;
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.semantifyr',
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
      'file:///workspace/snippet.semantifyr',
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
      return {
        status: 'valid',
        message: null,
        portfolioId: 'smart',
        report: sampleValidationReport,
      };
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    const getAutoValidateRequest = (): AutoValidateRequest => {
      return {
        command: 'oxsts.case.validateWitness',
        portfolioId: 'smart',
      };
    };

    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.semantifyr',
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
    expect(states.some((state) => {
      return state.cases[0]?.validating === true;
    })).toBe(true);
    expect(states.at(-1)!.cases[0]!.validationReport).toEqual(sampleValidationReport);
  });

  it('sends the case location and the witness its verification wrote when the command validates by case', async () => {
    const gammaCase = { ...sampleCase('a'), location: { uri: 'file:///workspace/snippet.gamma', range: { start: { line: 4, character: 0 }, end: { line: 6, character: 1 } } } };
    const sendRequest = vi.fn<LspClient['sendRequest']>(async (_method, parameters) => {
      if ((parameters as { command?: string }).command === 'gamma.case.verify') {
        return failedResult;
      }
      return { status: 'valid', message: null, portfolioId: 'smart', report: null };
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
      { getAutoValidateRequest: () => {
        return { command: 'gamma.witness.validate', byCase: true };
      } },
    );

    await vi.waitFor(() => {
      expect(states.at(-1)!.cases[0]!.witnessValidation).toBe('valid');
    });
    const parameters = sendRequest.mock.calls[1]![1] as { command: string; arguments: { uri: string; range: unknown; witnessUri?: string }[] };
    expect(parameters.command).toBe('gamma.witness.validate');
    expect(parameters.arguments[0]!.uri).toBe(gammaCase.location.uri);
    expect(parameters.arguments[0]!.range).toEqual(gammaCase.location.range);
    expect(parameters.arguments[0]!.witnessUri).toBe(failedResult.witnessUri);
  });

  it('skips auto-validation when the trace has no witnessUri', async () => {
    const noWitnessFail: VerificationCaseResult = { ...failedResult, witnessUri: null };
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => {
      return noWitnessFail;
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    const getAutoValidateRequest = (): AutoValidateRequest => {
      return { command: 'oxsts.case.validateWitness' };
    };

    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.semantifyr',
      [sampleCase('a')],
      setState,
      noopProgress,
      { getAutoValidateRequest },
    );

    await vi.waitFor(() => {
      expect(states.at(-1)?.phase).toBe('done');
    });
    expect(sendRequest).toHaveBeenCalledTimes(1);
    expect(states.some((state) => {
      return state.cases[0]?.validating === true;
    })).toBe(false);
  });
});

describe('verification statuses', () => {
  it.each([
    ['timeout', 'timeout'],
    ['cancelled', 'cancelled'],
    ['inconclusive', 'inconclusive'],
    ['mystery', 'errored'],
  ] as const)('maps a %s result to the %s case status', async (wireStatus, caseStatus) => {
    const response = { ...passedResult, status: wireStatus, message: 'note', report: null };
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => {
      return response;
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    runAllVerifications(
      client,
      'oxsts.case.verify',
      'file:///workspace/snippet.semantifyr',
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
});

describe('validateWitness', () => {
  it('collapses a thrown LSP error into an errored outcome with the message', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => {
      throw new Error('boom');
    });
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
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => {
      return {
        status: 'valid',
        portfolioId: 'smart',
        report: sampleValidationReport,
      };
    });
    const client: LspClient = { sendRequest, sendNotification: () => {} };

    const { states, setState } = captureStates();
    setState({
      phase: 'done',
      discovery: 'discovered',
      cases: [{ caseInfo: sampleCase('a'), status: 'failed', report: sampleReport, witnessUri: failedResult.witnessUri! }],
    });

    dispatchAutoValidation(
      client,
      'oxsts.case.validateWitness',
      'a',
      { uri: 'inmemory:///workspace/snippet.witness.semantifyr', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } },
      setState,
      { validationPortfolioId: 'smart' },
    );

    const intermediate = states.at(-1)!;
    expect(intermediate.cases[0]!.validating).toBe(true);

    await vi.waitFor(() => {
      const last = states.at(-1)!;
      expect(last.cases[0]!.witnessValidation).toBe('valid');
      expect(last.cases[0]!.validating).toBe(false);
      expect(last.cases[0]!.validationReport).toEqual(sampleValidationReport);
      expect(last.cases[0]!.validationPortfolioIdUsed).toBe('smart');
    });
  });
});

describe('the wait of a case', () => {
  const caseInfo = { ...sampleCase('a'), location: { uri: 'file:///workspace/snippet.semantifyr', range: { start: { line: 3, character: 0 }, end: { line: 3, character: 1 } } } };
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
    const sendRequest = vi.fn<LspClient['sendRequest']>(() => {
      return new Promise((resolve) => {
        answer = resolve;
      });
    });
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
