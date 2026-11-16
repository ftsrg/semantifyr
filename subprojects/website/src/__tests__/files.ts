/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

// a Windows checkout writes CRLF, which the tests' patterns do not expect
export function readText(path: string): string {
  return readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
}

// the files below a directory, each by its path from it written with '/', as on every platform the repository names its files
export function filesUnder(directory: string): string[] {
  return readdirSync(directory, { recursive: true, withFileTypes: true })
    .filter((entry) => {
      return entry.isFile();
    })
    .map((entry) => {
      return relative(directory, join(entry.parentPath, entry.name)).split(sep).join('/');
    });
}
