/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { isOutcome, outcomeTerms, type Outcome } from '@semantifyr/editor-common/terms';

const OUTCOMES = Object.keys(outcomeTerms) as Outcome[];

export function outcomeOf(value: string | undefined, table: string, row: string): Outcome {
  if (value === undefined || !isOutcome(value)) {
    throw new Error(`${table} has the outcome ${value} in the row ${row}, where the outcomes are ${OUTCOMES.join(', ')}`);
  }
  return value;
}

export function warningOf(value: string | undefined, table: string, row: string): boolean {
  if (value !== 'yes' && value !== 'no') {
    throw new Error(`${table} has the warning ${value} in the row ${row}, where a warning is yes or no`);
  }
  return value === 'yes';
}

export function OutcomeCell({ outcome, warning }: { outcome: Outcome; warning: boolean }): React.JSX.Element {
  return (
    <td>
      {outcomeTerms[outcome].term}
      {warning ? ', with a warning' : ''}
    </td>
  );
}

export function OutcomeLegend(): React.JSX.Element {
  return (
    <dl>
      {OUTCOMES.map((outcome) => {
        return (
          <div key={outcome}>
            <dt>
              <strong>{outcomeTerms[outcome].term}</strong>
            </dt>
            <dd>{outcomeTerms[outcome].meaning}</dd>
          </div>
        );
      })}
    </dl>
  );
}
