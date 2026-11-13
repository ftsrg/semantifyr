/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import file from './terms.json' with { type: 'json' };

export interface Term {
  term: string;
  short?: string;
  meaning: string;
  rejected: string[];
  // the one page that may use a rejected word, where it explains it
  explainedOn?: string;
}

export type TermId = keyof typeof file.terms;

export type Verdict = keyof typeof file.verdicts;

export const terms: Readonly<Record<TermId, Term>> = file.terms;

export const portfolioNames: Readonly<Record<string, string>> = file.portfolios;

export const verdictWords: Readonly<Record<Verdict, string>> = {
  passed: file.verdicts.passed.word,
  failed: file.verdicts.failed.word,
  inconclusive: file.verdicts.inconclusive.word,
  error: file.verdicts.error.word,
};
