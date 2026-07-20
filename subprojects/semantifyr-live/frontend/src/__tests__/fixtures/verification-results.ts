/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type {
  VerificationCaseResult,
  VerificationMetrics,
  VerificationTrace,
} from '@semantifyr/editor-common';

export const sampleMetrics: VerificationMetrics = {
  totalDuration: 'PT1S',
  preparationDuration: 'PT0.1S',
  verificationDuration: 'PT0.8S',
  backAnnotationDuration: 'PT0S',
};

export const sampleTrace: VerificationTrace = {
  callTrace: { initialStep: { traces: [] }, steps: [] },
  witnessState: { initialStep: { values: [] }, steps: [] },
  witnessUri: 'inmemory:///workspace/snippet.witness.oxsts',
};

export const passedResult: VerificationCaseResult = {
  status: 'passed',
  message: null,
  backendId: 'theta-cegar',
  portfolioId: 'smart-full',
  metrics: sampleMetrics,
  trace: null,
};

export const failedResult: VerificationCaseResult = {
  status: 'failed',
  message: 'property fails',
  backendId: 'theta-cegar',
  portfolioId: 'smart-full',
  metrics: sampleMetrics,
  trace: sampleTrace,
};
