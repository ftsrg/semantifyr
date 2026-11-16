/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerificationCaseResult, VerificationReport } from '@semantifyr/editor-common';

export const sampleReport = {
  version: 2,
  kind: 'run',
  verificationCase: 'example::Case',
  portfolioId: 'smart',
  result: {
    metadata: { backendId: 'theta-cegar', startedAt: '2026-11-16T10:00:00Z' },
    verdict: 'Failed',
    soundness: 'UnderApproximating',
    metrics: { totalDuration: 'PT1S' },
    message: 'property fails',
  },
  trace: {
    root: 'root',
    transitions: [],
    variables: [],
    initial: { state: 0, calls: [] },
    states: [{ values: {} }],
    steps: [],
    missingCalls: null,
  },
} as unknown as VerificationReport;

export const sampleValidationReport: VerificationReport = { ...sampleReport, kind: 're-check', trace: null };

export const passedResult: VerificationCaseResult = {
  status: 'passed',
  message: null,
  portfolioId: 'smart',
  report: { ...sampleReport, trace: null },
  witnessUri: null,
};

export const failedResult: VerificationCaseResult = {
  status: 'failed',
  message: 'property fails',
  portfolioId: 'smart',
  report: sampleReport,
  witnessUri: 'inmemory:///workspace/snippet.witness.semantifyr',
};
