/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { CaseManifest } from '@semantifyr/editor-common/examples';
import { describe, expect, it } from 'vitest';

// Goal: every lesson's stage opens in live as a reader can follow it, with a few cases, each expected to pass or to fail

const LESSON_STAGES = fileURLToPath(new URL('../../../../oxsts-test-models/tutorial/', import.meta.url));

const manifest = JSON.parse(readFileSync(`${LESSON_STAGES}manifest.json`, 'utf8')) as CaseManifest;

describe.each(manifest.models.map((model) => {
  return [model.file, model] as const;
}))('the lesson stage %s', (_, model) => {
  it('has at most ten cases, so that a reader is not overwhelmed', () => {
    expect(model.cases.length).toBeGreaterThan(0);
    expect(model.cases.length).toBeLessThanOrEqual(10);
  });

  it('expects each case to pass or to fail', () => {
    for (const declaredCase of model.cases) {
      expect(['Passed', 'Failed'], declaredCase.name).toContain(declaredCase.verdict);
    }
  });
});
