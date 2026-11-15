/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
  resultTraceOf,
  type ResultTrace,
  type VerificationCaseResult,
  type VerificationMetrics,
  type VerificationReport,
} from '@semantifyr/editor-common';

export const sampleMetrics: VerificationMetrics = {
  totalDuration: 'PT1S',
  preparationDuration: 'PT0.1S',
  verificationDuration: 'PT0.8S',
  backAnnotationDuration: 'PT0S',
};

export const sampleReport = {
  version: 2,
  kind: 'run',
  verificationCase: 'example::Case',
  portfolioId: 'smart',
  result: { verdict: 'Failed', message: 'property fails' },
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

export const passedResult: VerificationCaseResult = {
  status: 'passed',
  message: null,
  backendId: 'theta-cegar',
  portfolioId: 'smart',
  metrics: sampleMetrics,
  report: { ...sampleReport, trace: null },
  witnessUri: null,
};

export const failedResult: VerificationCaseResult = {
  status: 'failed',
  message: 'property fails',
  backendId: 'theta-cegar',
  portfolioId: 'smart',
  metrics: sampleMetrics,
  report: sampleReport,
  witnessUri: 'inmemory:///workspace/snippet.witness.oxsts',
};

export const sampleTrace: ResultTrace = resultTraceOf(failedResult)!;
