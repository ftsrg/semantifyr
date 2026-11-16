/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { AROUND, BACKENDS, MODEL_CHECKER, PATH } from '../components/Develop/ComponentPath';
import { readText } from './files';

// Goal: every box of the architecture's picture leads to a section of the page, which the site's build only warns about when an anchor is missing

const ARCHITECTURE = readText(fileURLToPath(new URL('../docs/develop/index.md', import.meta.url)));

describe('the architecture page', () => {
  it('leads every box of the path to its section', () => {
    expect([...PATH, BACKENDS, MODEL_CHECKER, ...AROUND].filter((box) => {
      return !ARCHITECTURE.includes(`{#${box.section}}`);
    }).map((box) => {
      return box.id;
    })).toEqual([]);
  });
});
