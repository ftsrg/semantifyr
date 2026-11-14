/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import type { Outcome } from '@semantifyr/editor-common/terms';
import { withCode } from '@site/src/lib/inlineCode';
import { OutcomeCell, OutcomeLegend, outcomeOf, warningOf } from './ConstructOutcome';

const TABLE = 'the SysML construct table';
const HEADER = 'construct\toutcome\twarning\tdetail';

interface SysmlConstruct {
  construct: string;
  outcome: Outcome;
  warning: boolean;
  detail: string;
}

function parseConstructs(table: string): SysmlConstruct[] {
  const [header, ...rows] = table.replace(/\r\n/g, '\n').trimEnd().split('\n');
  if (header !== HEADER) {
    throw new Error(`${TABLE} must start with the header ${HEADER}`);
  }
  return rows.map((row) => {
    const [construct, outcome, warning, detail] = row.split('\t');
    if (construct === undefined || detail === undefined) {
      throw new Error(`${TABLE} has a malformed row: ${row}`);
    }
    return { construct, outcome: outcomeOf(outcome, TABLE, row), warning: warningOf(warning, TABLE, row), detail };
  });
}

export function SysmlConstructsOf({ table, outcome }: { table: string; outcome: Outcome }): React.JSX.Element {
  return (
    <ul>
      {parseConstructs(table)
        .filter((construct) => {
          return construct.outcome === outcome;
        })
        .map(({ construct, detail }) => {
          return (
            <li key={construct}>
              <strong>{withCode(construct)}</strong>: {withCode(detail)}
            </li>
          );
        })}
    </ul>
  );
}

export default function SysmlConstructTable({ table }: { table: string }): React.JSX.Element {
  const constructs = parseConstructs(table);
  return (
    <>
      <OutcomeLegend />
      <table>
        <thead>
          <tr>
            <th>Construct</th>
            <th>Outcome</th>
            <th>Detail</th>
          </tr>
        </thead>
        <tbody>
          {constructs.map((construct) => {
            return (
              <tr key={construct.construct}>
                <td>{withCode(construct.construct)}</td>
                <OutcomeCell outcome={construct.outcome} warning={construct.warning} />
                <td>{withCode(construct.detail)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </>
  );
}
