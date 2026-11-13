/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { verdictOfLauncherVerdict, verdictWords } from '@semantifyr/editor-common/terms';

export default function LauncherVerdicts({ verdicts }: { verdicts: string[] }): React.JSX.Element {
  const mapped = Object.keys(verdictOfLauncherVerdict);
  for (const verdict of mapped) {
    if (!verdicts.includes(verdict)) {
      throw new Error(`The terms file maps ${verdict}, which no launcher prints`);
    }
  }
  return (
    <table>
      <thead>
        <tr>
          <th>Launcher</th>
          <th>Live</th>
        </tr>
      </thead>
      <tbody>
        {verdicts.map((verdict) => {
          const live = verdictOfLauncherVerdict[verdict];
          if (live === undefined) {
            throw new Error(`The terms file gives no Live verdict for the launcher's ${verdict}`);
          }
          return (
            <tr key={verdict}>
              <td>
                <code>{verdict}</code>
              </td>
              <td>{verdictWords[live]}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
