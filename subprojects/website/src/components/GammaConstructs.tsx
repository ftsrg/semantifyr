/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';

type Outcome = 'carried' | 'departure' | 'planned' | 'out';

interface GammaConstruct {
  area: string;
  name: string;
  outcome: Outcome;
  detail: string;
}

const HEADER = 'area\tconstruct\toutcome\tfixture\tmarker\tdetail';
const OUTCOMES: readonly string[] = ['carried', 'departure', 'planned', 'out'];

const STATUS: Record<Outcome, string> = {
  carried: 'Carried',
  departure: 'Carried with a departure',
  planned: 'Planned',
  out: 'Out',
};

function parseConstructs(table: string): GammaConstruct[] {
  const [header, ...rows] = table.replace(/\r\n/g, '\n').trimEnd().split('\n');
  if (header !== HEADER) {
    throw new Error(`gamma constructs.tsv must start with the header ${HEADER}`);
  }
  return rows.map((row) => {
    const [area, name, outcome, , , detail] = row.split('\t');
    if (area === undefined || name === undefined || outcome === undefined || detail === undefined || !OUTCOMES.includes(outcome)) {
      throw new Error(`gamma constructs.tsv has a malformed row: ${row}`);
    }
    return { area, name, outcome: outcome as Outcome, detail };
  });
}

function withCode(text: string): React.ReactNode[] {
  return text.split('`').map((part, index) => (index % 2 === 1 ? <code key={index}>{part}</code> : part));
}

export function GammaDepartures({ table }: { table: string }): React.JSX.Element {
  return (
    <ul>
      {parseConstructs(table)
        .filter(({ outcome }) => outcome === 'departure')
        .map(({ name, detail }) => (
          <li key={name}>
            <strong>{withCode(name)}.</strong> {withCode(detail)}
          </li>
        ))}
    </ul>
  );
}

export default function GammaConstructTable({ table }: { table: string }): React.JSX.Element {
  const constructs = parseConstructs(table);
  const areas = [...new Set(constructs.map(({ area }) => area))];
  return (
    <>
      {areas.map((area) => (
        <section key={area}>
          <h3>{area}</h3>
          <table>
            <thead>
              <tr>
                <th>Construct</th>
                <th>Status</th>
                <th>Note</th>
              </tr>
            </thead>
            <tbody>
              {constructs
                .filter((construct) => construct.area === area)
                .map((construct) => (
                  <tr key={construct.name}>
                    <td>{withCode(construct.name)}</td>
                    <td>{STATUS[construct.outcome]}</td>
                    <td>{withCode(construct.detail)}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </section>
      ))}
    </>
  );
}
