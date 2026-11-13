/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { withCode } from '@site/src/lib/inlineCode';

interface ExitCodeEntry {
  code: number;
  meaning: string;
}

// the launchers' ExitCode, written by the generatedReference_exit-codes Gradle task
export default function ExitCodeTable({ codes }: { codes: ExitCodeEntry[] }): React.JSX.Element {
  return (
    <table>
      <thead>
        <tr>
          <th>Code</th>
          <th>Meaning</th>
        </tr>
      </thead>
      <tbody>
        {codes.map((entry) => {
          return (
            <tr key={entry.code}>
              <td>{entry.code}</td>
              <td>{withCode(entry.meaning)}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
