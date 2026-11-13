/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';

const HEADER = 'construct\tstatus\tdetail';
const STATUSES: Record<string, string> = {
  supported: 'Supported',
  warned: 'Supported with a warning',
  refused: 'Refused',
};

interface SysmlConstruct {
  construct: string;
  status: string;
  detail: string;
}

function parseConstructs(table: string): SysmlConstruct[] {
  const [header, ...rows] = table.replace(/\r\n/g, '\n').trimEnd().split('\n');
  if (header !== HEADER) {
    throw new Error(`the SysML construct table must start with the header ${HEADER}`);
  }
  return rows.map((row) => {
    const [construct, status, detail] = row.split('\t');
    if (construct === undefined || status === undefined || detail === undefined || STATUSES[status] === undefined) {
      throw new Error(`the SysML construct table has a malformed row: ${row}`);
    }
    return { construct, status, detail };
  });
}

function withCode(text: string): React.ReactNode[] {
  return text.split('`').map((part, index) => (index % 2 === 1 ? <code key={index}>{part}</code> : part));
}

export default function SysmlConstructTable({ table }: { table: string }): React.JSX.Element {
  return (
    <table>
      <thead>
        <tr>
          <th>Construct</th>
          <th>Status</th>
          <th>Detail</th>
        </tr>
      </thead>
      <tbody>
        {parseConstructs(table).map((construct) => (
          <tr key={construct.construct}>
            <td>{withCode(construct.construct)}</td>
            <td>{STATUSES[construct.status]}</td>
            <td>{construct.detail}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
