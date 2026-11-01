/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Admonition from '@theme/Admonition';

type Outcome = 'pass' | 'fail' | 'reject' | 'parse';

interface Construct {
  name: string;
  outcome: Outcome;
  detail: string;
}

const HEADER = 'construct\toutcome\tfixture\tdetail';
const OUTCOMES: readonly string[] = ['pass', 'fail', 'reject', 'parse'];

function parseConstructs(table: string): Construct[] {
  const [header, ...rows] = table.replace(/\r\n/g, '\n').trimEnd().split('\n');
  if (header !== HEADER) {
    throw new Error(`constructs.tsv must start with the header ${HEADER}`);
  }
  return rows.map((row) => {
    const [name, outcome, , detail] = row.split('\t');
    if (name === undefined || outcome === undefined || detail === undefined || !OUTCOMES.includes(outcome)) {
      throw new Error(`constructs.tsv has a malformed row: ${row}`);
    }
    return { name, outcome: outcome as Outcome, detail };
  });
}

function findConstruct(table: string, name: string): Construct {
  const construct = parseConstructs(table).find((candidate) => candidate.name === name);
  if (construct === undefined) {
    throw new Error(`constructs.tsv lists no construct ${name}`);
  }
  return construct;
}

function statusOf({ outcome }: Construct): string {
  switch (outcome) {
    case 'pass':
    case 'fail':
      return 'Supported';
    case 'reject':
      return 'Rejected by the validator';
    case 'parse':
      return 'Planned';
  }
}

function withCode(text: string): React.ReactNode[] {
  return text.split('`').map((part, index) => (index % 2 === 1 ? <code key={index}>{part}</code> : part));
}

export function ConstructStatus({ table, construct }: { table: string; construct: string }): React.JSX.Element {
  return <>{statusOf(findConstruct(table, construct)).toLowerCase()}</>;
}

export function Planned({ table, construct, children }: { table: string; construct: string; children: React.ReactNode }): React.JSX.Element {
  const { outcome } = findConstruct(table, construct);
  if (outcome !== 'parse') {
    throw new Error(`constructs.tsv says the parser no longer rejects ${construct} (${outcome}); update the page that marks it Planned`);
  }
  return (
    <Admonition type="info" title="Planned">
      {children}
    </Admonition>
  );
}

export default function ConstructTable({ table }: { table: string }): React.JSX.Element {
  return (
    <table>
      <thead>
        <tr>
          <th>Construct</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {parseConstructs(table).map((construct) => (
          <tr key={construct.name}>
            <td>{withCode(construct.name)}</td>
            <td>
              {statusOf(construct)}
              {construct.outcome === 'reject' && (
                <>
                  {' '}
                  (<code>{construct.detail}</code>)
                </>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
