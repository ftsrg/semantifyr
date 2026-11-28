/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import type { ActiveVerificationInfo } from '../../lib/api/types';
import {
  applyServerReport,
  markQueued,
  unmarkQueued,
  type VerificationCaseState,
  type VerificationState,
} from '../../lib/verification/state';
import { sampleCase } from '../fixtures/cases';
import {
  failedResult,
  sampleReport,
} from '../fixtures/verification-results';

describe('the server\'s report of the session\'s verifications', () => {
  const caseOnLine = (id: string, line: number): VerificationCaseState['caseInfo'] => {
    return {
      id,
      label: `Case ${id}`,
      location: { uri: 'file:///workspace/snippet.semantifyr', range: { start: { line, character: 2 }, end: { line, character: 9 } } },
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
      cases: [{ caseInfo: caseOnLine('a', 1), status: 'failed', validating: true, report: sampleReport, witnessUri: failedResult.witnessUri! }],
    };
    const witness = { uri: failedResult.witnessUri!, range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } };
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
