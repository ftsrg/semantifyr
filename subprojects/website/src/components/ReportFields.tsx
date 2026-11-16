/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';

interface ReportField {
  name: string;
  type: string;
  nullable: boolean;
  values: string[];
  fields: ReportField[];
}

function Fields({ fields }: { fields: ReportField[] }): React.JSX.Element {
  return (
    <ul>
      {fields.map((field) => {
        return (
          <li key={field.name}>
            <code>{field.name}</code> {field.type}
            {field.values.length > 0 && (
              <>
                {': '}
                {field.values.map((value, index) => {
                  return (
                    <span key={value}>
                      {index > 0 && ', '}
                      <code>{value}</code>
                    </span>
                  );
                })}
              </>
            )}
            {field.nullable && ', or null'}
            {field.fields.length > 0 && <Fields fields={field.fields} />}
          </li>
        );
      })}
    </ul>
  );
}

// report.json's fields, written by the generatedReference_report-fields Gradle task from the report's serializable type
export default function ReportFields({ fields }: { fields: ReportField[] }): React.JSX.Element {
  return <Fields fields={fields} />;
}
