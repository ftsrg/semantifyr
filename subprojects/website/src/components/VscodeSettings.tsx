/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { withCode } from '@site/src/lib/inlineCode';

interface Setting {
  default?: unknown;
  markdownDescription?: string;
  description?: string;
}

interface Manifest {
  contributes: {
    configuration: {
      properties: Record<string, Setting>;
    };
  };
}

function defaultOf(setting: Setting): React.ReactNode {
  if (setting.default === '') {
    return 'empty';
  }
  return <code>{String(setting.default)}</code>;
}

// the settings of the VS Code extension, from its manifest
export default function VscodeSettings({ manifest }: { manifest: Manifest }): React.JSX.Element {
  return (
    <table>
      <thead>
        <tr>
          <th>Setting</th>
          <th>Default</th>
          <th>Description</th>
        </tr>
      </thead>
      <tbody>
        {Object.entries(manifest.contributes.configuration.properties).map(([key, setting]) => {
          const description = setting.markdownDescription ?? setting.description;
          if (description === undefined) {
            throw new Error(`The extension's setting ${key} has no description`);
          }
          return (
            <tr key={key}>
              <td>
                <code>{key}</code>
              </td>
              <td>{defaultOf(setting)}</td>
              <td>{withCode(description)}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
