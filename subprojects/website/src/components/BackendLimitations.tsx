/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';

interface Limitation {
  backend: string;
  configuration: string;
  construct: string;
  reason: string;
}

const HEADER = 'backend\tconfiguration\tconstruct\treason';

function parseLimitations(table: string): Limitation[] {
  const [header, ...rows] = table.replace(/\r\n/g, '\n').trimEnd().split('\n');
  if (header !== HEADER) {
    throw new Error(`limitations.tsv must start with the header ${HEADER}`);
  }
  return rows.map((row) => {
    const [backend, configuration, construct, reason] = row.split('\t');
    if (backend === undefined || configuration === undefined || construct === undefined || reason === undefined) {
      throw new Error(`limitations.tsv has a malformed row: ${row}`);
    }
    return { backend, configuration, construct, reason };
  });
}

export default function BackendLimitations({ table, backend }: { table: string; backend: string }): React.JSX.Element {
  const limitations = parseLimitations(table).filter((limitation) => limitation.backend === backend);
  if (limitations.length === 0) {
    throw new Error(`limitations.tsv lists nothing for the backend ${backend}`);
  }
  return (
    <table>
      <thead>
        <tr>
          <th>Refused construct</th>
          <th>Why</th>
        </tr>
      </thead>
      <tbody>
        {limitations.map((limitation) => (
          <tr key={`${limitation.configuration}:${limitation.construct}`}>
            <td>
              {limitation.construct}
              {limitation.configuration !== '' && (
                <>
                  {' '}
                  (the <code>{limitation.configuration}</code> configuration)
                </>
              )}
            </td>
            <td>{limitation.reason}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
