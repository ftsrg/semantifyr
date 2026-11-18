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
  trace: {
    root: 'root',
    transitions: [],
    variables: [],
    initial: { state: 0, calls: [] },
    states: [{ values: {} }],
    steps: [],
    missingCalls: null,
  },
})

export const sampleValidationReport: VerificationReport = { ...sampleReport, kind: 're-check', trace: null };

export const passedResult: VerificationCaseResult = {
  status: 'passed',
  message: null,
  portfolioId: 'smart',
  report: { ...sampleReport, trace: null },
  witnessUri: null,
  traceUri: null,
};

export const failedResult: VerificationCaseResult = {
  status: 'failed',
  message: 'property fails',
  portfolioId: 'smart',
  report: sampleReport,
  witnessUri: 'inmemory:///workspace/snippet.witness.semantifyr',
  traceUri: null,
};
