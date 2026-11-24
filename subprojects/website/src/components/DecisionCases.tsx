/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { withCode } from '@site/src/lib/inlineCode';

interface DecidingCase {
  model: string;
  name: string;
}

interface Decision {
  id: string;
  title: string;
  cases: DecidingCase[];
}

// written by the generatedReference_decision-cases Gradle task
export default function DecisionCases({ decisions }: { decisions: Decision[] }): React.JSX.Element {
  return (
    <table>
      <thead>
        <tr>
          <th>Decision</th>
          <th>Cases</th>
        </tr>
      </thead>
      <tbody>
        {decisions.map((decision) => {
          return (
            <tr key={decision.id}>
              <td>
                <a href={`#${decision.id}`}>{withCode(decision.title)}</a>
              </td>
              <td>
                {decision.cases.length === 0 ? 'No case' : (
                  <ul>
                    {decision.cases.map((decidingCase) => {
                      return (
                        <li key={`${decidingCase.model} ${decidingCase.name}`}>
                          <code>{decidingCase.name}</code> in <code>{decidingCase.model}</code>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
