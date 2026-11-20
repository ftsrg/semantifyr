/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerificationCaseResult, VerificationReport } from '@semantifyr/editor-common';

import { reportOf } from './reports';

export const sampleReport = reportOf({
  verdict: 'Failed',
  backendId: 'theta-cegar',
  seconds: 1,
  soundness: 'UnderApproximating',
  message: 'property fails',
  trace: {
    transitions: [],
    variables: [],
    initial: { state: 0, calls: [] },
    states: [{ values: {} }],
    steps: [],
  },
})

export const sampleValidationReport: VerificationReport = { ...sampleReport, kind: 're-check', trace: null };

export const passedResult: VerificationCaseResult = {
  status: null,
  message: null,
  portfolioId: null,
  report: reportOf({ verdict: 'Passed', backendId: 'theta-cegar', seconds: 1 }),
  witnessUri: null,
  traceUri: null,
};

export const failedResult: VerificationCaseResult = {
  status: null,
  message: null,
  portfolioId: null,
  report: sampleReport,
  witnessUri: 'inmemory:///workspace/snippet.witness.semantifyr',
  traceUri: null,
};
