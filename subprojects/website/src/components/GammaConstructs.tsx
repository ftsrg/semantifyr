/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { outcomeTerms, type Outcome } from '@semantifyr/editor-common/terms';
import { withCode } from '@site/src/lib/inlineCode';
import { OutcomeCell, OutcomeLegend, outcomeOf, warningOf } from './ConstructOutcome';

const TABLE = 'gamma constructs.tsv';
const HEADER = 'area\tconstruct\toutcome\twarning\tfixture\tmarker\tdetail';

const DIFFERENCES: readonly Outcome[] = [
  'abstracted',
  'deviating',
  'extension',
];

interface GammaConstruct {
  area: string;
  name: string;
  outcome: Outcome;
  warning: boolean;
  detail: string;
}

function parseConstructs(table: string): GammaConstruct[] {
  const [header, ...rows] = table.replace(/\r\n/g, '\n').trimEnd().split('\n');
  if (header !== HEADER) {
    throw new Error(`${TABLE} must start with the header ${HEADER}`);
  }
  return rows.map((row) => {
    const [area, name, outcome, warning, , , detail] = row.split('\t');
    if (area === undefined || name === undefined || detail === undefined) {
      throw new Error(`${TABLE} has a malformed row: ${row}`);
    }
    return { area, name, outcome: outcomeOf(outcome, TABLE, row), warning: warningOf(warning, TABLE, row), detail };
  });
}

export function GammaDifferences({ table }: { table: string }): React.JSX.Element {
  return (
    <ul>
      {parseConstructs(table)
        .filter(({ outcome }) => {
          return DIFFERENCES.includes(outcome);
        })
        .map(({ name, outcome, detail }) => {
          return (
            <li key={name}>
              <strong>{withCode(name)}</strong>, {outcomeTerms[outcome].term.toLowerCase()}. {withCode(detail)}
            </li>
          );
        })}
    </ul>
  );
}

export default function GammaConstructTable({ table }: { table: string }): React.JSX.Element {
  const constructs = parseConstructs(table);
  const areas = [...new Set(constructs.map(({ area }) => {
    return area;
  }))];
  return (
    <>
      <OutcomeLegend />
      {areas.map((area) => {
        return (
          <section key={area}>
            <h3>{area}</h3>
            <table>
              <thead>
                <tr>
                  <th>Construct</th>
                  <th>Outcome</th>
                  <th>Note</th>
                </tr>
              </thead>
              <tbody>
                {constructs
                  .filter((construct) => {
                    return construct.area === area;
                  })
                  .map((construct) => {
                    return (
                      <tr key={construct.name}>
                        <td>{withCode(construct.name)}</td>
                        <OutcomeCell outcome={construct.outcome} warning={construct.warning} />
                        <td>{withCode(construct.detail)}</td>
                      </tr>
                    );
                  })}
              </tbody>
            </table>
          </section>
        );
      })}
    </>
  );
}
