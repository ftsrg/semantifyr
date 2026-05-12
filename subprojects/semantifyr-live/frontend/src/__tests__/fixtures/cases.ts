/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerificationCaseSpecification } from '@semantifyr/editor-common';

/**
 * Canonical {@link VerificationCaseSpecification} fixture for tests. The label is derived from
 * the supplied id so a test that emits multiple cases (`'a'`, `'b'`, ...) gets human-readable
 * labels (`Case a`, `Case b`) without each test having to assemble them.
 *
 * <p>The location URI matches the in-memory snippet other fixtures use; tests that don't
 * assert on it can ignore it.
 */
export function sampleCase(id: string): VerificationCaseSpecification {
  return {
    id,
    label: `Case ${id}`,
    location: {
      uri: 'inmemory:///workspace/snippet.oxsts',
      range: {
        start: { line: 0, character: 0 },
        end: { line: 0, character: 0 },
      },
    },
  };
}
