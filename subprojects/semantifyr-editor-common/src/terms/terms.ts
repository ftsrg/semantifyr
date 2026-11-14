/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerificationStatus } from '../wire.ts';
import file from './terms.json' with { type: 'json' };

export interface Term {
  term: string;
  short?: string;
  meaning: string;
  rejected: string[];
  // the one page that may use a rejected word, where it explains it
  explainedOn?: string;
}

export interface OutcomeTerm extends Term {
  // a construct of this outcome is among the differences a frontend lists from its source language
  difference: boolean;
}

export type TermId = keyof typeof file.terms;

export type Verdict = keyof typeof file.verdicts;

export type Outcome = keyof typeof file.outcomes;

export const terms: Readonly<Record<TermId, Term>> = file.terms;

export const outcomeTerms: Readonly<Record<Outcome, OutcomeTerm>> = file.outcomes;

export const outcomeWarning: string = file.outcomeWarning;

export function isOutcome(value: string): value is Outcome {
  return Object.hasOwn(file.outcomes, value);
}

export const portfolioNames: Readonly<Record<string, string>> = file.portfolios;

export const verdictWords: Readonly<Record<Verdict, string>> = {
  passed: file.verdicts.passed.word,
  failed: file.verdicts.failed.word,
  inconclusive: file.verdicts.inconclusive.word,
  error: file.verdicts.error.word,
};

export const verdictOfLauncherVerdict: Readonly<Record<string, Verdict>> = Object.fromEntries(
  (Object.keys(file.verdicts) as Verdict[]).flatMap((verdict) => {
    return file.verdicts[verdict].launcher.map((launcherVerdict) => {
      return [launcherVerdict, verdict];
    });
  }),
);

export type ResultStatus = Exclude<VerificationStatus, typeof VerificationStatus.Cancelled>;

// the terms file cannot be typed by its verdicts' lists, so a status it misses fails here and in its test, never as a default
export function verdictOfStatus(status: ResultStatus): Verdict {
  const verdict = verdictOfLauncherVerdict[status.toUpperCase()];
  if (verdict === undefined) {
    throw new Error(`The terms map no verdict to the status ${status}`);
  }
  return verdict;
}
